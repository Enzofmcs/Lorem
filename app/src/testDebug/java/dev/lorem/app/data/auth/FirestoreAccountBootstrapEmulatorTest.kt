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
import com.google.firebase.firestore.MemoryLruGcSettings
import com.google.firebase.firestore.Source
import dev.lorem.app.domain.auth.AuthenticationFailure
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
import org.robolectric.annotation.LooperMode
import dev.lorem.app.data.handles.*
import dev.lorem.app.data.local.RoomHandlesCache
import dev.lorem.app.data.local.LoremDatabase
import androidx.room.Room
import dev.lorem.app.data.cloud.FirestoreTrainingRepository
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.RecommendIpsum
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import java.time.Instant

/** Opt-in SDK integration; never uses the real project in google-services.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FirestoreAccountBootstrapEmulatorTest {
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val cacheDatabases = mutableListOf<LoremDatabase>()
    private var submitted = emptyList<CodeforcesSubmission>()
    private var cfRating: Int? = 3500
    private var cfUnavailable = false
    private var cfComplete = true
    private val cf = object : CodeforcesRepository {
        override suspend fun user(handle: String) = UserLookupResult.Success(CodeforcesUser("Tourist", "Tourist", cfRating, System.currentTimeMillis()))
        override suspend fun submissionHistory(handle: String): SubmissionHistoryResult = if (cfUnavailable)
            SubmissionHistoryResult.NetworkFailure else SubmissionHistoryResult.Success(submitted, verificationComplete = cfComplete)
        override suspend fun problems() = ProblemCatalogResult.Success((1L..4L).map {
            CodeforcesProblem(ProblemId(2000 + it, "A"), "Problem $it", 800, setOf("math"),
                VerifiedProblemOrigin(2000 + it, VerifiedProblemOrigin.CUTOFF_EPOCH_MILLIS, System.currentTimeMillis()))
        })
    }
    private val scheduled = mutableListOf<CloudIpsum>()
    private fun repository(): FirestoreTrainingRepository {
        val cache = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LoremDatabase::class.java).build()
        cacheDatabases.add(cache)
        return FirestoreTrainingRepository(db, { auth.currentUser?.uid }, cf,
            AccountHandlesRepository(FirestoreHandlesStore(auth, db), cf, RoomHandlesCache(cache.accountHandlesDao())),
            CloudDeadline(android.os.SystemClock::elapsedRealtime) { 1 }, android.os.SystemClock::elapsedRealtime,
            { _, attempt, _ -> synchronized(scheduled) { scheduled.add(attempt) } }, RecommendIpsum { 0 })
    }

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
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().setGcSettings(
                    MemoryLruGcSettings.newBuilder().setSizeBytes(10L * 1024 * 1024).build()).build()).build()
            useEmulator(firestoreHost.first, firestoreHost.second)
        }
    }

    @After fun disconnect() = runBlocking {
        cacheDatabases.forEach { it.close() }
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

    @Test(timeout = 60_000) fun `production handles are canonical concurrent and always start 800 without resetting`() = runBlocking {
        val uid = signInGoogle()
        FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
        val repository = repository()
        val profiles = (1..5).map { async(Dispatchers.IO) { repository.addHandle(uid, "TOURIST") } }.awaitAll()
        assertEquals(1, profiles.distinct().size)
        assertEquals(800, profiles.first().rating)
        assertEquals("cf_tourist", profiles.first().key)
        cfRating = null
        assertEquals(profiles.first(), repository.addHandle(uid, "tourist"))
        assertEquals(1, repository.load(uid).handles.size)
    }

    @Test(timeout = 60_000) fun `production new unrated CF handle starts at 800`() = runBlocking {
        val uid = signInGoogle()
        FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
        cfRating = null
        val repository = repository()
        assertEquals(800, repository.addHandle(uid, "tourist").rating)
        assertEquals(0L, repository.load(uid).handles.single().version)
    }

    @Test(timeout = 90_000) fun `production SDK lock survives recovery repeated start and applies D012 once`() = runBlocking {
        val uid = signInGoogle()
        FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
        val repository = repository()
        repository.addHandle(uid, "tourist")
        repository.selectHandle(uid, "cf_tourist")
        val active = repository.start(uid, "cf_tourist", "command")
        assertEquals(active, repository.start(uid, "cf_tourist", "command"))
        assertTrue(scheduled.any { it.id == active.id })
        assertEquals(active, repository().load(uid).active)
        try { repository.start(uid, "cf_tourist", "second"); fail("Second active") } catch (_: IllegalStateException) { }
        // CF has second precision; make a real eligible timestamp and let server time reach it.
        val acceptedAt = (active.ipsum.startedAtEpochMillis / 1000 + 1) * 1000
        kotlinx.coroutines.delay((acceptedAt - System.currentTimeMillis()).coerceAtLeast(0) + 30)
        submitted = listOf(CodeforcesSubmission(123, active.ipsum.problem.id, "OK", Instant.ofEpochMilli(acceptedAt)))
        assertTrue(repository.reconcile(uid, active))
        assertTrue(repository.reconcile(uid, active))
        val restored = repository().load(uid, "cf_tourist")
        assertNull(restored.active)
        assertEquals(1L, restored.handles.single().version)
        assertEquals(824, restored.handles.single().rating)
        assertEquals(CloudIpsumState.FINAL, restored.attempts.single().state)
        assertEquals(824, repository.addHandle(uid, "Tourist").rating)
    }

    @Test(timeout = 90_000) fun `production SDK persists hint manual close and no floor then isolates stale uid`() = runBlocking {
        val uid = signInGoogle()
        FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
        val repository = repository()
        repository.addHandle(uid, "tourist")
        repository.selectHandle(uid, "cf_tourist")
        val active = repository.start(uid, "cf_tourist", "manual")
        repository.hint(uid, active)
        assertNotNull(repository.load(uid).active!!.ipsum.hintRevealedAtEpochMillis)
        repository.close(uid, active, IpsumFailureReason.LOGIC_NOT_FOUND)
        val final = repository.load(uid, "cf_tourist")
        assertNull(final.active)
        assertEquals(780, final.handles.single().rating)
        assertEquals(IpsumStatus.PENDING, final.attempts.single().ipsum.status)
        assertNotNull(final.attempts.single().ipsum.hintRevealedAtEpochMillis)
        auth.signOut()
        try { repository.load(uid); fail("Stale uid") } catch (_: IllegalStateException) { }
    }

    @Test(timeout = 90_000) fun `production timeout waits on failure testing and incomplete window then accepts deadline AC once`() = runBlocking {
        val uid = signInGoogle()
        FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
        val repository = repository()
        repository.addHandle(uid, "tourist")
        repository.selectHandle(uid, "cf_tourist")
        val active = repository.start(uid, "cf_tourist", "timeout-ac")
        seedExpiredStartOnlyInEmulator(uid, active.id)
        val expired = repository.load(uid).active!!
        cfUnavailable = true
        assertFalse(repository.reconcile(uid, expired))
        var state = repository.load(uid, "cf_tourist")
        assertEquals(CloudIpsumState.CLOSED_AWAITING_VERIFICATION, state.active!!.state)
        assertEquals(800, state.handles.single().rating)
        cfUnavailable = false
        submitted = listOf(CodeforcesSubmission(1, expired.ipsum.problem.id, "TESTING",
            Instant.ofEpochMilli(expired.expiresAtMillis)))
        assertFalse(repository.reconcile(uid, expired))
        submitted = emptyList()
        cfComplete = false
        assertFalse(repository.reconcile(uid, expired))
        cfComplete = true
        submitted = listOf(CodeforcesSubmission(1, expired.ipsum.problem.id, "OK",
            Instant.ofEpochMilli(expired.expiresAtMillis / 1000 * 1000)))
        assertTrue(repository.reconcile(uid, expired))
        assertTrue(repository.reconcile(uid, expired))
        state = repository.load(uid, "cf_tourist")
        assertNull(state.active)
        assertEquals(812, state.handles.single().rating)
        assertEquals(1L, state.handles.single().version)
        assertEquals(IpsumStatus.COMPLETED, state.attempts.single().ipsum.status)
    }

    @Test(timeout = 90_000) fun `production noAC timeout preserves submillisecond boundary and upsolve never reapplies rating`() = runBlocking {
        val uid = signInGoogle()
        FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
        val repository = repository()
        repository.addHandle(uid, "tourist")
        repository.selectHandle(uid, "cf_tourist")
        val active = repository.start(uid, "cf_tourist", "timeout-failure")
        seedExpiredStartOnlyInEmulator(uid, active.id)
        val expired = repository.load(uid).active!!
        assertTrue(repository.reconcile(uid, expired))
        var state = repository.load(uid, "cf_tourist")
        assertEquals(780, state.handles.single().rating)
        assertEquals(IPSUM_LIMIT_MILLIS, state.attempts.single().ipsum.totalTimeMillis())
        assertEquals(CloudCloseReason.TIME_LIMIT_EXCEEDED, state.attempts.single().closeReason)
        val document = db.collection("users").document(uid).collection("handles").document("cf_tourist")
            .collection("ipsums").document(active.id).get(Source.SERVER).await()
        val originalStart = document.getTimestamp("startedAt")!!
        assertEquals(originalStart.nanoseconds, document.getTimestamp("cutoffAt")!!.nanoseconds)
        assertEquals(document.getTimestamp("cutoffAt"), (document.get("result") as Map<*, *>)["endedAt"])
        submitted = listOf(CodeforcesSubmission(2, expired.ipsum.problem.id, "OK",
            Instant.ofEpochMilli((expired.expiresAtMillis / 1000 + 1) * 1000)))
        assertTrue(repository.reconcile(uid, expired))
        state = repository.load(uid, "cf_tourist")
        assertEquals(780, state.handles.single().rating)
        assertEquals(1L, state.handles.single().version)
        assertEquals(IpsumStatus.SOLVED_OUTSIDE, state.attempts.single().ipsum.status)
    }

    // The SDK completes cached document reads on Android's main looper.
    @LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
    @Test(timeout = 90_000) fun `production offline cache preserves active but cannot confirm new commands`() = runBlocking {
        val uid = signInGoogle()
        FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
        val repository = repository()
        repository.addHandle(uid, "tourist")
        repository.selectHandle(uid, "cf_tourist")
        val active = repository.start(uid, "cf_tourist", "online")
        val online = repository.load(uid, "cf_tourist")
        db.disableNetwork().await()
        try {
            val cached = repository.load(uid, "cf_tourist")
            assertFalse(cached.online)
            assertEquals(active, cached.active)
            assertEquals(online.handles, cached.handles)
            try { repository.start(uid, "cf_tourist", "offline"); fail("New start must require server") }
            catch (_: com.google.firebase.firestore.FirebaseFirestoreException) { }
            catch (_: IllegalStateException) { }
        } finally { db.enableNetwork().await() }
        var restored = repository.load(uid, "cf_tourist")
        kotlinx.coroutines.withTimeout(10_000) {
            while (!restored.online) { kotlinx.coroutines.delay(100); restored = repository.load(uid, "cf_tourist") }
        }
        assertTrue(restored.online)
        assertEquals(active.id, restored.active!!.id)
        assertEquals(800, restored.handles.single().rating)
        assertEquals(0L, restored.handles.single().version)
    }

    @Test(timeout = 90_000) fun `merged handles contract survives training and releases selection without resetting account data`() = runBlocking {
        val uid = signInGoogle()
        FirestoreAccountBootstrap(auth, db).ensureAccount(uid)
        // Create with the exact implementation already merged in story 35.
        val store = FirestoreHandlesStore(auth, db)
        val old = store.create(uid, dev.lorem.app.domain.handles.HandleIdentity.parse("Tourist"))
        store.create(uid, dev.lorem.app.domain.handles.HandleIdentity.parse("Petr"))
        store.select(uid, old.identity.key, old.bindingVersion)
        val repository = repository()
        assertEquals("cf_tourist", repository.selectedHandle(uid))
        // Confirming server time does not create a lock or prevent the old selector from working.
        assertNull(repository.load(uid).active)
        store.select(uid, "cf_petr", 1)
        assertEquals("cf_petr", repository().selectedHandle(uid))
        try { repository.start(uid, "cf_tourist", "stale-selection"); fail("Stale selection") }
        catch (_: IllegalStateException) { }
        store.select(uid, "cf_tourist", 1)
        val active = repository.start(uid, "cf_tourist", "merged-contract")
        assertEquals(1L, active.bindingVersion)
        try { store.select(uid, "cf_petr", 1); fail("Switch during active training") }
        catch (e: dev.lorem.app.domain.handles.HandleException) {
            assertEquals(dev.lorem.app.domain.handles.HandleFailure.IPSUM_ACTIVE, e.failure)
        }
        repository.close(uid, active, IpsumFailureReason.LOGIC_NOT_FOUND)
        val updated = store.find(uid, "cf_tourist")!!
        assertEquals(old.createdAtEpochMillis, updated.createdAtEpochMillis)
        assertEquals(old.bindingVersion, updated.bindingVersion)
        assertEquals(780, updated.loremRating)
        assertEquals(1L, updated.progressVersion)
        assertFalse(db.document("users/$uid/runtime/current").get(Source.SERVER).await().exists())
        store.select(uid, "cf_petr", 1)
        val observed = kotlinx.coroutines.withTimeout(10_000) { store.observe(uid).first { it.serverConfirmed } }
        assertEquals("petr", observed.selection!!.handle)
        assertFalse(observed.locked)
        assertEquals(780, observed.handles.first { it.identity.key == "cf_tourist" }.loremRating)
        assertEquals("cf_petr", repository().selectedHandle(uid))
    }

    /** Synthetic clock fixture via the local emulator's rules-disabled test token, never a production endpoint. */
    private fun seedExpiredStartOnlyInEmulator(uid: String, id: String) {
        check(System.getenv("LOREM_FIREBASE_EMULATORS") == "true")
        val host = loopback("FIRESTORE_EMULATOR_HOST")
        val instant = Instant.ofEpochSecond(System.currentTimeMillis() / 1000 - 7203, 123_000)
        val name = "projects/demo-lorem-local/databases/(default)/documents/users/$uid/handles/cf_tourist/ipsums/$id"
        val url = java.net.URL("http://${host.first}:${host.second}/v1/projects/demo-lorem-local/databases/(default)/documents:commit")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer owner")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        val update = JSONObject().put("name", name).put("fields", JSONObject().put("startedAt",
            JSONObject().put("timestampValue", instant.toString())))
        val write = JSONObject().put("update", update).put("updateMask", JSONObject().put("fieldPaths", org.json.JSONArray().put("startedAt")))
        connection.outputStream.use { it.write(JSONObject().put("writes", org.json.JSONArray().put(write)).toString().toByteArray()) }
        try { assertEquals(200, connection.responseCode) } finally { connection.disconnect() }
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
