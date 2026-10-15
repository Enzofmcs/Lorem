package dev.lorem.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import dev.lorem.app.domain.sync.*
import java.io.File
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SynchronizationPersistenceTest {
    @get:Rule val folder = TemporaryFolder()

    private fun database(file: File) = Room.databaseBuilder(
        ApplicationProvider.getApplicationContext(), LoremDatabase::class.java, file.absolutePath,
    ).build()

    private fun local(file: File, scope: CoroutineScope, db: LoremDatabase): Pair<LocalLoremRepository, PreferencesSynchronizationStore> {
        val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return LocalLoremRepository(preferences, db.problemHistoryDao(), db.problemCatalogDao(), db.ipsumDao()) to
            PreferencesSynchronizationStore(preferences)
    }

    @Test fun `cancellation retains partial cache cursor and failure without claiming success`() = runTest {
        val db = database(folder.newFile("cancel.db"))
        val (local, store) = local(folder.newFile("cancel.preferences_pb"), backgroundScope, db)
        local.saveProfile(profile())
        val remote = object : Pages((6L downTo 1L).map { submission(it) }) {
            override suspend fun submissionPage(handle: String, from: Int, count: Int): SubmissionHistoryResult {
                if (from > 1) throw CancellationException("Stopped")
                return super.submissionPage(handle, from, count)
            }
        }
        val result = runCatching { SynchronizeProblemHistory(local, remote, store = store, pageSize = 4, nowMillis = { 100_000 })() }
        assertTrue(result.exceptionOrNull() is CancellationException)
        val state = store.read(syncKey(SyncDataset.HISTORY, local.profileLink.first()))
        assertEquals(3, state.nextFrom)
        assertEquals(4, local.problemHistory.first().size)
        assertFalse(state.running)
        assertNotNull(state.failure)
        assertNull(state.lastSuccessEpochMillis)
        assertEquals(0L, local.profile.first()!!.lastSyncEpochMillis)
        db.close()
    }

    @Test fun `equal timestamps remain eligible incrementally and datasets report partial success separately`() = runTest {
        val db = database(folder.newFile("incremental.db"))
        val (local, store) = local(folder.newFile("incremental.preferences_pb"), backgroundScope, db)
        local.saveProfile(profile())
        val remote = object : Pages(listOf(submission(10, at = 1_000_000))) {
            override suspend fun problems(): ProblemCatalogResult = ProblemCatalogResult.NetworkFailure
        }
        val coordinator = SynchronizationCoordinator(local, remote, backgroundScope, store, { 2_000_000 })
        assertEquals(ProblemHistorySyncResult.Success(1), coordinator.synchronizeHistory())
        remote.calls.clear()
        remote.values = listOf(submission(11, at = 1_000_000), submission(10, at = 1_000_000)) +
            (9L downTo 1L).map { submission(it, at = it * 10_000) }
        coordinator.synchronizeHistory()
        // The boundary is reached on page one; old pages need no further request.
        assertEquals(listOf(1), remote.calls)
        assertTrue(local.problemHistory.first().any { it.problemId == ProblemId(11, "A") })
        coordinator.synchronizeCatalog()
        val history = store.read(syncKey(SyncDataset.HISTORY, local.profileLink.first()))
        val catalog = store.read(syncKey(SyncDataset.CATALOG, null))
        assertNotNull(history.lastSuccessEpochMillis)
        assertNull(history.failure)
        assertNull(catalog.lastSuccessEpochMillis)
        assertNotNull(catalog.failure)
        db.close()
    }

    @Test fun `interrupted initial import resumes durable cursor and sweeps new head before readiness`() = runTest {
        val dbFile = folder.newFile("resume.db")
        val prefs = folder.newFile("resume.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val db = database(dbFile)
        val (first, store) = local(prefs, scope, db)
        first.saveProfile(profile())
        val remote = Pages((10L downTo 1L).map { submission(it, if (it == 1L) "TESTING" else "OK") })
        remote.failFrom = 3
        assertEquals(ProblemHistorySyncResult.NetworkFailure,
            SynchronizeProblemHistory(first, remote, store = store, pageSize = 4, nowMillis = { 100_000 })())
        val link = first.profileLink.first()!!
        val key = syncKey(SyncDataset.HISTORY, link)
        assertEquals(3, store.read(key).nextFrom)
        assertEquals(0L, first.profile.first()!!.lastSyncEpochMillis)
        assertEquals(4, first.problemHistory.first().size)
        assertNull(store.read(key).lastSuccessEpochMillis)
        assertNotNull(store.read(key).failure)
        db.close(); scope.cancel()

        val reopenedDb = database(dbFile)
        val (reopened, restoredStore) = local(prefs, backgroundScope, reopenedDb)
        assertEquals(link, reopened.profileLink.first())
        remote.failFrom = null
        remote.calls.clear()
        remote.values = listOf(submission(11)) + remote.values
        assertEquals(ProblemHistorySyncResult.Success(11),
            SynchronizeProblemHistory(reopened, remote, store = restoredStore, pageSize = 4, nowMillis = { 200_000 })())
        assertEquals(3, remote.calls.first())
        assertTrue(remote.calls.contains(1))
        assertEquals(200_000L, reopened.profile.first()!!.lastSyncEpochMillis)
        val completed = restoredStore.read(key)
        assertEquals(200_000L, completed.lastSuccessEpochMillis)
        assertEquals(mapOf(1L to 1_000L), completed.pendingSubmissions)

        // An old TESTING verdict remains in the incremental query after the
        // newest timestamp moves far beyond the normal one-minute overlap.
        remote.values = listOf(submission(99, at = 9_000_000)) + remote.values.map {
            if (it.id == 1L) it.copy(verdict = "OK") else it
        }
        SynchronizeProblemHistory(reopened, remote, store = restoredStore, pageSize = 4, nowMillis = { 300_000 })()
        assertTrue(reopened.problemHistory.first().single { it.problemId.contestId == 1L }.hasAcceptedSubmission)
        assertTrue(restoredStore.read(key).pendingSubmissions.isEmpty())
        assertEquals(12, reopened.problemHistory.first().size)
        reopenedDb.close()
    }

    @Test fun `page persistence failure does not advance cursor and retry keeps accepted evidence`() = runTest {
        val db = database(folder.newFile("failure.db"))
        val (base, store) = local(folder.newFile("failure.preferences_pb"), backgroundScope, db)
        base.saveProfile(profile())
        var fail = true
        val local = object : LoremRepository by base {
            override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) {
                if (fail && history.any { it.problemId.contestId == 2L }) throw java.io.IOException("disk")
                base.saveProblemHistory(ownerHandle, history)
            }
        }
        val remote = Pages((6L downTo 1L).map { submission(it) })
        assertEquals(ProblemHistorySyncResult.PersistenceFailure,
            SynchronizeProblemHistory(local, remote, store = store, pageSize = 4, nowMillis = { 100_000 })())
        val key = syncKey(SyncDataset.HISTORY, base.profileLink.first())
        assertEquals(3, store.read(key).nextFrom)
        assertEquals(0L, base.profile.first()!!.lastSyncEpochMillis)
        fail = false
        remote.values = remote.values.map { it.copy(verdict = "WRONG_ANSWER") }
        assertEquals(ProblemHistorySyncResult.Success(6),
            SynchronizeProblemHistory(local, remote, store = store, pageSize = 4, nowMillis = { 200_000 })())
        assertTrue(base.problemHistory.first().single { it.problemId.contestId == 6L }.hasAcceptedSubmission)
        assertFalse(base.problemHistory.first().single { it.problemId.contestId == 1L }.hasAcceptedSubmission)
        db.close()
    }

    @Test fun `duplicate callers share work and obsolete A B A response cannot commit`() = runTest {
        val db = database(folder.newFile("aba.db"))
        val (local, store) = local(folder.newFile("aba.preferences_pb"), backgroundScope, db)
        local.saveProfile(profile())
        val firstLink = local.profileLink.first()!!
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = object : Pages(listOf(submission(1))) {
            override suspend fun submissionPage(handle: String, from: Int, count: Int): SubmissionHistoryResult {
                calls += from
                entered.complete(Unit)
                // Model an HTTP client that finishes despite cancellation.
                withContext(NonCancellable) { release.await() }
                return SubmissionHistoryResult.Success(values)
            }
        }
        val coordinator = SynchronizationCoordinator(local, remote, backgroundScope, store, { 100_000 })
        val one = async { coordinator.synchronizeHistory() }
        entered.await()
        val two = async { coordinator.synchronizeHistory() }
        runCurrent()
        assertEquals(1, remote.calls.size)
        local.saveProfile(profile("B"))
        local.saveProfile(profile(" TOURIST ").copy(loremRating = 1888, consolidatedRating = 1500))
        assertNotEquals(firstLink, local.profileLink.first())
        release.complete(Unit)
        runCurrent()
        assertTrue(runCatching { one.await() }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { two.await() }.exceptionOrNull() is CancellationException)
        assertTrue(local.problemHistory.first().isEmpty())
        assertEquals(1888, local.profile.first()!!.loremRating)
        assertEquals(1500, local.profile.first()!!.consolidatedRating)
        assertEquals(0L, local.profile.first()!!.lastSyncEpochMillis)
        val previous = local.profileLink.first()
        local.clearProfile()
        local.saveProfile(profile())
        assertNotEquals(previous, local.profileLink.first())
        db.close()
    }

    @Test fun `profile refresh and history preserve rating committed while remote request is suspended`() = runTest {
        val db = database(folder.newFile("rating.db"))
        val (local, store) = local(folder.newFile("rating.preferences_pb"), backgroundScope, db)
        local.saveProfile(profile())
        val active = local.createActiveIpsum(ipsum())
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val remote = object : Pages(listOf(submission(4))) {
            override suspend fun user(handle: String): UserLookupResult {
                entered.complete(Unit); release.await()
                return UserLookupResult.Success(CodeforcesUser("Tourist", "Updated", 2200))
            }
        }
        val coordinator = SynchronizationCoordinator(local, remote, backgroundScope, store, { 100_000 })
        val refresh = async { coordinator.refreshProfile() }
        entered.await()
        local.recordIpsumSubmissions(active.id, listOf(IpsumSubmission(88, active.id, "OK", 60_000)))
        val rated = local.profile.first()!!.loremRating
        release.complete(Unit); refresh.await()
        coordinator.synchronizeHistory()
        assertEquals(rated, local.profile.first()!!.loremRating)
        assertEquals("Updated", local.profile.first()!!.displayName)
        assertEquals(2200, local.profile.first()!!.officialRating)
        assertNotNull(store.read(syncKey(SyncDataset.PROFILE, local.profileLink.first())).lastSuccessEpochMillis)
        assertNotNull(store.read(syncKey(SyncDataset.HISTORY, local.profileLink.first())).lastSuccessEpochMillis)
        assertNull(store.read(syncKey(SyncDataset.CATALOG, null)).lastSuccessEpochMillis)
        db.close()
    }

    @Test fun `concurrent checks closure and upsolve apply one original rating`() = runTest {
        val db = database(folder.newFile("closure.db"))
        val (local, store) = local(folder.newFile("closure.preferences_pb"), backgroundScope, db)
        local.saveProfile(profile())
        val active = local.createActiveIpsum(ipsum())
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val remote = object : Pages(emptyList()) {
            override suspend fun submissionHistorySince(handle: String, sinceEpochMillis: Long): SubmissionHistoryResult {
                entered.complete(Unit); release.await()
                return SubmissionHistoryResult.Success(listOf(
                    submission(1, "WRONG_ANSWER", 10_000).copy(problemId = active.problem.id),
                    submission(2, "TESTING", 20_000).copy(problemId = active.problem.id),
                    submission(3, "OK", 60_000).copy(problemId = active.problem.id),
                ))
            }
        }
        val coordinator = SynchronizationCoordinator(local, remote, backgroundScope, store, { 100_000 })
        val check = async { coordinator.verifyActiveIpsum() }
        entered.await()
        local.endIpsumWithoutAc(active.id, IpsumFailureReason.TIME_EXPIRED, 50_000)
        val original = local.getIpsumResult(active.id)!!.ipsum
        release.complete(Unit); check.await()
        val results = awaitAll(async { coordinator.updatePendingIpsums() }, async { coordinator.updatePendingIpsums() })
        assertTrue(results.all { it is UpdatePendingIpsumsResult.Success })
        val solved = local.getIpsumResult(active.id)!!.ipsum
        assertEquals(IpsumStatus.SOLVED_OUTSIDE, solved.status)
        assertEquals(original.finalLoremRating, solved.finalLoremRating)
        assertEquals(original.ratingDelta, solved.ratingDelta)
        assertEquals(original.endedAtEpochMillis, solved.endedAtEpochMillis)
        assertEquals(original.finalLoremRating, local.profile.first()!!.loremRating)
        coordinator.updatePendingIpsums()
        assertEquals(solved, local.getIpsumResult(active.id)!!.ipsum)
        db.close()
    }

    @Test fun `late check belongs to original Ipsum and cannot update the next attempt status`() = runTest {
        val db = database(folder.newFile("next.db"))
        val (local, store) = local(folder.newFile("next.preferences_pb"), backgroundScope, db)
        local.saveProfile(profile())
        val first = local.createActiveIpsum(ipsum())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = object : Pages(emptyList()) {
            override suspend fun submissionHistorySince(handle: String, sinceEpochMillis: Long): SubmissionHistoryResult {
                entered.complete(Unit); release.await()
                return SubmissionHistoryResult.Success(listOf(submission(1, at = 60_000).copy(problemId = first.problem.id)))
            }
        }
        val coordinator = SynchronizationCoordinator(local, remote, backgroundScope, store, { 100_000 })
        val oldCheck = async { coordinator.verifyActiveIpsum() }
        entered.await()
        local.endIpsumWithoutAc(first.id, IpsumFailureReason.TIME_EXPIRED, 50_000)
        val next = local.createActiveIpsum(ipsum().copy(
            problem = ipsum().problem.copy(id = ProblemId(51, "B")), startedAtEpochMillis = 55_000,
            initialLoremRating = local.profile.first()!!.loremRating,
        ))
        val link = local.profileLink.first()!!
        assertEquals(VerifyIpsumResult.NoActiveIpsum, VerifyIpsumSubmissions(local, remote)(link, first.id))
        release.complete(Unit)
        assertFalse((oldCheck.await() as VerifyIpsumResult.Success).completed)
        assertEquals(next, local.activeIpsum.first())
        assertNull(store.read(syncKey(SyncDataset.SUBMISSIONS, link, next.id)).lastSuccessEpochMillis)
        assertNotNull(store.read(syncKey(SyncDataset.SUBMISSIONS, link, first.id)).lastSuccessEpochMillis)
        assertEquals(IpsumStatus.PENDING, local.getIpsumResult(first.id)!!.ipsum.status)
        db.close()
    }

    private open class Pages(var values: List<CodeforcesSubmission>) : CodeforcesRepository {
        val calls = mutableListOf<Int>()
        var failFrom: Int? = null
        override suspend fun user(handle: String): UserLookupResult = error("unused")
        override suspend fun problems(): ProblemCatalogResult = error("unused")
        override suspend fun submissionHistory(handle: String) = SubmissionHistoryResult.Success(values)
        override suspend fun submissionPage(handle: String, from: Int, count: Int): SubmissionHistoryResult {
            calls += from
            return if (from == failFrom) SubmissionHistoryResult.NetworkFailure
                else SubmissionHistoryResult.Success(values.drop(from - 1).take(count))
        }
    }

    private fun profile(handle: String = "tourist") = LocalProfile(handle, "Tourist", 1500, 1500, null, 0)
    private fun submission(id: Long, verdict: String = "OK", at: Long = id * 1_000) =
        CodeforcesSubmission(id, ProblemId(id, "A"), verdict, Instant.ofEpochMilli(at))
    private fun ipsum() = Ipsum(0, "tourist", CodeforcesProblem(ProblemId(50, "A"), "Training", 1500, setOf("math")),
        1500, IpsumCategory.CURRENT_LEVEL, 1500, 1599, 1500, 0, 1_000, status = IpsumStatus.ACTIVE)
}
