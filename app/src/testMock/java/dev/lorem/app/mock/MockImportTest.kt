package dev.lorem.app.mock

import android.app.Application
import android.os.Looper
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import dev.lorem.app.data.cloud.MockBatchReader
import dev.lorem.app.data.cloud.mockBatchesForUser
import dev.lorem.app.domain.buildIpsumHistory
import dev.lorem.app.domain.buildIpsumStatistics
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MockImportTest {
    private val clients = mutableListOf<Pair<FirebaseApp, FirebaseFirestore>>()

    private fun example() = requireNotNull(javaClass.getResourceAsStream("/ipsums-200-exemplo.xlsx"))
    private fun workbook() = example().use(::readMockWorkbook)

    private fun editSheet(edit: (String) -> String): MockWorkbook {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { target ->
            ZipInputStream(example()).use { source ->
                while (true) {
                    val entry = source.nextEntry ?: break
                    target.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "xl/worksheets/sheet1.xml") {
                        target.write(edit(source.readBytes().decodeToString()).encodeToByteArray())
                    } else {
                        source.copyTo(target)
                    }
                    target.closeEntry()
                }
            }
        }
        return output.toByteArray().inputStream().use(::readMockWorkbook)
    }

    private fun client(): FirebaseFirestore {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "mock-test-${UUID.randomUUID()}"
        val db = mockFirestoreClient(context, name)
        clients += FirebaseApp.getInstance(name) to db
        return db
    }

    private fun cloudEmulatorClient(): Pair<FirebaseAuth, FirebaseFirestore> {
        val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FirebaseOptions.Builder()
            .setProjectId("demo-lorem-ipsums-cloud")
            .setApplicationId("1:123456789000:android:0000000000000000000000")
            .setApiKey("AIza" + "0".repeat(35)).build(), "mock-cloud-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9093) }
        val db = FirebaseFirestore.getInstance(app).apply { useEmulator("127.0.0.1", 8083) }
        clients += app to db
        return auth to db
    }

    private suspend fun googleEmulatorUid(auth: FirebaseAuth, subject: String = UUID.randomUUID().toString()): String {
        fun encode(json: JSONObject) = Base64.encodeToString(json.toString().toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val token = encode(JSONObject().put("alg", "none").put("typ", "JWT")) + "." +
            encode(JSONObject().put("sub", subject).put("email", "$subject@example.invalid")
                .put("email_verified", true).put("iss", "https://accounts.google.com").put("aud", "emulator-only")
                .put("iat", System.currentTimeMillis() / 1000).put("exp", System.currentTimeMillis() / 1000 + 3600)) + "."
        val uid = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user!!.uid
        shadowOf(Looper.getMainLooper()).idle()
        return uid
    }

    @After fun close() = runBlocking {
        for ((app, db) in clients) {
            db.terminate().await()
            app.delete()
        }
    }

    @Test fun `the delivered Excel normalizes native dates and has 200 distinct problems`() {
        val sample = workbook()
        assertEquals(200, sample.ipsums.size)
        assertEquals(1_735_754_400_000L, sample.ipsums.first().startedAt)
        assertEquals(200, sample.ipsums.map { it.contestId to it.index }.toSet().size)
        assertEquals(mapOf(MockOutcome.AC to 160, MockOutcome.PENDENTE to 20, MockOutcome.UPSOLVE to 20),
            sample.ipsums.groupingBy(MockIpsum::outcome).eachCount())
        assertEquals(sample.id, workbook().id)
        assertEquals(sample.id, editSheet { it.replace("Problema simulado 001", " Problema simulado 001 ") }.id)
    }

    @Test fun `duplicates formulas and fewer rows are rejected before Firestore`() {
        val duplicate = assertThrows(WorkbookException::class.java) {
            editSheet { it.replace("""<c r="B3" t="inlineStr"><is><t>B</t></is></c>""",
                """<c r="B3" t="inlineStr"><is><t>A</t></is></c>""") }
        }
        assertTrue(duplicate.message!!.contains("já usado na linha 2"))
        val formula = assertThrows(WorkbookException::class.java) {
            editSheet { it.replace("""<c r="G2" s="2" t="n"><v>20</v></c>""",
                """<c r="G2"><f>10+10</f><v>20</v></c>""") }
        }
        assertTrue(formula.message!!.contains("fórmulas"))
        val missing = assertThrows(WorkbookException::class.java) {
            editSheet { it.replace(Regex("""<row r="201".*?</row>"""), "") }
        }
        assertTrue(missing.message!!.contains("199"))
    }

    @Test(timeout = 60_000) fun `SDK imports 200 records once and restores history and score`() = runBlocking {
        assumeTrue(System.getenv("LOREM_MOCK_EMULATOR") == "true")
        val sample = editSheet { it.replace("Problema simulado 001", "SDK ${UUID.randomUUID()}") }
        val firstDb = client()
        val first = MockFirestore(firstDb)
        val second = MockFirestore(client())
        val results = listOf(async { first.import(sample) }, async { second.import(sample) }).awaitAll()
        assertEquals(1, results.count { it.inserted })
        assertFalse(second.import(sample).inserted)
        val saved = results.first().history
        assertEquals(200, buildIpsumHistory(saved.ipsums).size)
        val stats = buildIpsumStatistics(saved.profile, saved.ipsums)
        assertEquals(200, stats.overall.total)
        assertEquals(160, stats.overall.accepted)
        assertEquals(20, stats.overall.pending)
        assertEquals(20, stats.overall.solvedOutside)
        assertEquals(200, stats.ratingEvolution.size)
        assertEquals(800, stats.ratingEvolution.first().before)
        assertEquals(stats.currentRating, stats.ratingEvolution.last().after)
        assertEquals(saved, second.load(sample.id))
        val handle = firstDb.document("users/${sample.id}/handles/cf_mock_excel")
        assertEquals(200, handle.collection("usedProblems").get(Source.SERVER).await().size())
        assertFalse(saved.ipsums.first().problem.origin!!.source.startsWith("codeforces:"))
    }

    @Test(timeout = 60_000) fun `a refused write rolls back the full batch`() = runBlocking {
        assumeTrue(System.getenv("LOREM_MOCK_EMULATOR") == "true")
        val sample = editSheet { it.replace("Problema simulado 001", "Atomicidade ${UUID.randomUUID()}") }
        val db = client()
        val root = "users/${sample.id}"
        val handle = db.document("$root/handles/cf_mock_excel")
        handle.collection("usedProblems").document("900001_A").set(mapOf("ipsumId" to "conflict")).await()
        val error = runCatching { MockFirestore(db).import(sample) }.exceptionOrNull()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (error as FirebaseFirestoreException).code)
        assertFalse(db.document(root).get(Source.SERVER).await().exists())
        assertFalse(handle.get(Source.SERVER).await().exists())
        assertTrue(handle.collection("ipsums").get(Source.SERVER).await().isEmpty)
    }

    @Test(timeout = 60_000) fun `cloud batches are writable by their Google uid and private to it`() = runBlocking {
        assumeTrue(System.getenv("LOREM_MOCK_CLOUD_EMULATORS") == "true")
        val sample = workbook()
        val (ownerAuth, ownerDb) = cloudEmulatorClient()
        val subject = UUID.randomUUID().toString()
        val ownerUid = googleEmulatorUid(ownerAuth, subject)
        val store = MockFirestore.forUser(ownerDb, ownerUid)
        assertTrue(store.import(sample).inserted)
        assertFalse(store.import(sample).inserted)
        assertEquals(200, store.load(sample.id)?.ipsums?.size)

        val (viewerAuth, viewerDb) = cloudEmulatorClient()
        val viewerUid = googleEmulatorUid(viewerAuth, subject)
        assertEquals(ownerUid, viewerUid)
        val viewer = MockBatchReader(mockBatchesForUser(viewerDb, viewerUid))
        assertEquals(store.load(sample.id), viewer.load(sample.id))

        val (otherAuth, otherDb) = cloudEmulatorClient()
        val otherStore = MockFirestore.forUser(otherDb, ownerUid)
        assertDenied { otherStore.load(sample.id) }
        googleEmulatorUid(otherAuth)
        assertDenied { otherStore.load(sample.id) }
        assertDenied { otherStore.import(sample) }
        assertDenied { ownerDb.document("users/$ownerUid").get(Source.SERVER).await() }

        val (anonymousAuth, anonymousDb) = cloudEmulatorClient()
        val anonymousUid = anonymousAuth.signInAnonymously().await().user!!.uid
        shadowOf(Looper.getMainLooper()).idle()
        assertDenied { MockFirestore.forUser(anonymousDb, anonymousUid).import(sample) }
    }

    private suspend fun assertDenied(operation: suspend () -> Any?) {
        val failure = runCatching { operation() }.exceptionOrNull() as FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, failure.code)
    }
}
