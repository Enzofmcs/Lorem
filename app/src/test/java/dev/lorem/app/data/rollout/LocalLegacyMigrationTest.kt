package dev.lorem.app.data.rollout

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.data.local.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalLegacyMigrationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "rollout-${java.util.UUID.randomUUID()}.preferences_pb") }
    private val db = Room.inMemoryDatabaseBuilder(context, LoremDatabase::class.java).addCallback(LoremDatabase.ARCHIVE_CALLBACK).build()
    private val repo = LocalLoremRepository(store, db.problemHistoryDao(), db.problemCatalogDao(), db.ipsumDao(),
        archiveStarted = { db.legacyRolloutDao().state() != null })
    private var time = 1_800_000_000_000L
    private var response: SubmissionHistoryResult = SubmissionHistoryResult.Success(emptyList())
    private val cf = object : CodeforcesRepository {
        override suspend fun user(handle: String): UserLookupResult = error("No profile import")
        override suspend fun problems(): ProblemCatalogResult = error("No catalog mutation")
        override suspend fun submissionHistory(handle: String) = response
    }
    private fun migration() = LocalLegacyMigration(db, repo, cf, { time }, StudyPseudonyms(store))
    @After fun close() { scope.cancel(); db.close() }
    private suspend fun seed(active: Boolean = false): Ipsum? {
        repo.saveProfile(StatisticsFixtures.profile)
        StatisticsFixtures.finished.forEach { db.ipsumDao().insert(it.toEntity()) }
        repo.saveProblemHistory("tourist", listOf(ProblemHistory(ProblemId(9999, "A"), true, false)))
        return if (!active) null else repo.createActiveIpsum(StatisticsFixtures.attempt(0, IpsumStatus.ACTIVE).copy(
            startedAtEpochMillis = time - 7_201_000, initialLoremRating = 1202))
    }
    @Test fun `archive is device scoped idempotent and physically read only with historical snapshots intact`() = runBlocking {
        seed()
        val originals = db.legacyRolloutDao().attempts()
        val archived = migration().reconcile()
        assertTrue(archived.ready)
        assertEquals(originals, db.legacyRolloutDao().attempts())
        assertEquals(1202, archived.profile!!.loremRating)
        assertEquals(archived, migration().reconcile())
        try { repo.saveProfile(StatisticsFixtures.profile.copy(loremRating = 800)); fail("Frozen profile") } catch (_: IllegalStateException) { }
        try { db.ipsumDao().markSolvedOutsideOnce(3, time); fail("Frozen Room rows") } catch (_: android.database.SQLException) { }
        assertTrue(migration().exclusions("TOURIST").contains(ProblemId(9999, "A")))
        assertTrue(migration().exclusions("Petr").isEmpty())
        val exported = migration().export("tourist")
        assertEquals(4, exported.attempts.size)
        assertEquals(exported.participantKey, migration().export("TOURIST").participantKey)
    }
    @Test fun `expired active preserves start waits for complete CF and applies deadline only once`() = runBlocking {
        val active = seed(true)!!
        val originals = db.legacyRolloutDao().attempts().filter { it.id != active.id }
        response = SubmissionHistoryResult.NetworkFailure
        val waiting = migration().reconcile()
        assertFalse(waiting.ready)
        assertEquals(active.startedAtEpochMillis + 7_200_000, waiting.boundaryMillis)
        response = SubmissionHistoryResult.Success(emptyList(), verificationComplete = false)
        assertFalse(migration().reconcile().ready)
        response = SubmissionHistoryResult.Success(listOf(CodeforcesSubmission(1, active.problem.id, "TESTING", Instant.ofEpochMilli(waiting.boundaryMillis!!))))
        assertFalse(migration().reconcile().ready)
        response = SubmissionHistoryResult.Success(emptyList())
        val done = migration().reconcile()
        assertTrue(done.ready)
        val result = done.attempts.last()
        assertEquals(active.startedAtEpochMillis, result.startedAtEpochMillis)
        assertEquals(7_200_000L, result.totalTimeMillis())
        assertEquals(IpsumFailureReason.TIME_EXPIRED, result.failureReason)
        assertEquals(originals, db.legacyRolloutDao().attempts().filter { it.id != active.id })
        assertEquals(done, migration().reconcile())
    }
    @Test fun `late received eligible AC wins but an AC after deadline does not alter original evaluation`() = runBlocking {
        val active = seed(true)!!
        val boundary = active.startedAtEpochMillis + 7_200_000
        response = SubmissionHistoryResult.Success(listOf(CodeforcesSubmission(1, active.problem.id, "OK", Instant.ofEpochMilli(boundary))))
        val done = migration().reconcile()
        assertTrue(done.ready)
        assertEquals(IpsumStatus.COMPLETED, done.attempts.last().status)
        val before = db.legacyRolloutDao().attempts()
        response = SubmissionHistoryResult.Success(listOf(CodeforcesSubmission(2, active.problem.id, "OK", Instant.ofEpochMilli(boundary + 1000))))
        assertEquals(done, migration().reconcile())
        assertEquals(before, db.legacyRolloutDao().attempts())
    }
    @Test fun `concurrent migration instances finalize once and ignore stored verdicts outside the original deadline`() = runBlocking {
        val active = seed(true)!!
        val boundary = active.startedAtEpochMillis + 7_200_000
        db.ipsumDao().insertSubmissions(listOf(IpsumSubmissionEntity(80, active.id, "WRONG_ANSWER", boundary + 1000)))
        response = SubmissionHistoryResult.Success(listOf(
            CodeforcesSubmission(80, active.problem.id, "WRONG_ANSWER", Instant.ofEpochMilli(boundary + 1000)),
            CodeforcesSubmission(81, active.problem.id, "WRONG_ANSWER", Instant.ofEpochMilli(boundary - 1000))))
        val snapshots = (1..4).map { async(Dispatchers.IO) { migration().reconcile() } }.awaitAll()
        assertTrue(snapshots.all { it.ready })
        assertEquals(1, snapshots.map { it.attempts.last() }.distinct().size)
        val ended = snapshots.first().attempts.last()
        assertEquals(1, ended.errorCount)
        assertEquals(7_200_000L, ended.totalTimeMillis())
        assertEquals(2, db.ipsumDao().submissions(active.id).size)
    }
    @Test fun `live old attempt keeps its remaining time and manual boundary survives retry`() = runBlocking {
        seed()
        val active = repo.createActiveIpsum(StatisticsFixtures.attempt(0, IpsumStatus.ACTIVE).copy(startedAtEpochMillis = time - 60_000))
        assertFalse(migration().reconcile().ready)
        response = SubmissionHistoryResult.NetworkFailure
        val closing = migration().close(IpsumFailureReason.LOGIC_NOT_FOUND)
        assertEquals(time, closing.boundaryMillis)
        time += 8_000_000
        assertEquals(closing.boundaryMillis, migration().reconcile().boundaryMillis)
        response = SubmissionHistoryResult.Success(emptyList())
        val done = migration().reconcile().attempts.first { it.id == active.id }
        assertEquals(60_000L, done.totalTimeMillis())
        assertEquals(IpsumFailureReason.LOGIC_NOT_FOUND, done.failureReason)
    }
}
