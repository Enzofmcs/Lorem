package dev.lorem.app.data.sync

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.lorem.app.data.local.*
import dev.lorem.app.data.remote.CodeforcesRetryAfter
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import dev.lorem.app.domain.sync.*
import java.time.Instant
import java.util.concurrent.Executors
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
@Config(sdk = [35], application = Application::class)
class SynchronizationWorkTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var now = 10_000_000L

    private class Remote : CodeforcesRepository {
        var history: SubmissionHistoryResult = SubmissionHistoryResult.Success(emptyList())
        var catalog: ProblemCatalogResult = ProblemCatalogResult.Success(emptyList())
        var metadata: UserLookupResult = UserLookupResult.Success(CodeforcesUser("tourist", "Updated", 2500))
        var beforeHistory: suspend () -> Unit = {}
        var historyCalls = 0
        var catalogCalls = 0
        var userCalls = 0
        override suspend fun submissionHistory(handle: String): SubmissionHistoryResult { historyCalls++; beforeHistory(); return history }
        override suspend fun problems(): ProblemCatalogResult { catalogCalls++; return catalog }
        override suspend fun user(handle: String): UserLookupResult { userCalls++; return metadata }
    }

    private inner class Fixture(scope: CoroutineScope) : AutoCloseable {
        val db = Room.inMemoryDatabaseBuilder(context, LoremDatabase::class.java).build()
        val prefs = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.newFile("${System.nanoTime()}.preferences_pb") })
        val local = LocalLoremRepository(prefs, db.problemHistoryDao(), db.problemCatalogDao(), db.ipsumDao())
        val store = PreferencesSynchronizationStore(prefs)
        val remote = Remote()
        val coordinator = SynchronizationCoordinator(local, remote, scope, store, { now })
        val background = BackgroundSynchronization(local, coordinator, { now })
        val retryAfter = CodeforcesRetryAfter(store, { now })
        val factory = SynchronizationWorkerFactory(background, retryAfter::isDeferred)
        suspend fun link(): ProfileLink {
            if (local.profile.first() == null) local.saveProfile(LocalProfile("tourist", "Tourist", 1500, 1234, 1100, 0))
            return local.profileLink.first()!!
        }
        fun worker(data: Data) = TestListenableWorkerBuilder<SynchronizationWorker>(context)
            .setWorkerFactory(factory).setInputData(data).build()
        override fun close() = db.close()
    }

    @Test fun `workers prepare independently and retry temporary failures without redoing successful metadata`() = runTest {
        Fixture(backgroundScope).use { f ->
            val link = f.link()
            f.remote.history = SubmissionHistoryResult.NetworkFailure
            assertEquals(ListenableWorker.Result.retry(), f.worker(SynchronizationScheduler.profileData(link)).doWork())
            assertEquals(ListenableWorker.Result.success(), f.worker(SynchronizationScheduler.catalogData()).doWork())
            assertNotNull(f.store.read(syncKey(SyncDataset.PROFILE, link)).lastSuccessEpochMillis)
            assertNull(f.store.read(syncKey(SyncDataset.HISTORY, link)).lastSuccessEpochMillis)
            assertNotNull(f.store.read(syncKey(SyncDataset.CATALOG, null)).lastSuccessEpochMillis)
            f.remote.history = SubmissionHistoryResult.Success(emptyList())
            assertEquals(ListenableWorker.Result.success(), f.worker(SynchronizationScheduler.profileData(link)).doWork())
            assertEquals(1, f.remote.userCalls)
            assertEquals(now, f.local.profile.first()!!.lastSyncEpochMillis)
            assertEquals(1234, f.local.profile.first()!!.loremRating)
            assertEquals(1100, f.local.profile.first()!!.consolidatedRating)
            assertEquals(2500, f.local.profile.first()!!.officialRating)
        }
    }

    @Test fun `permanent errors stop retry loops while HTTP server errors and rate limits retry`() = runTest {
        Fixture(backgroundScope).use { f ->
            val link = f.link()
            for (result in listOf(SubmissionHistoryResult.InvalidResponse, SubmissionHistoryResult.HttpFailure(403), SubmissionHistoryResult.Failed("handle not found"))) {
                f.remote.history = result
                assertEquals(ListenableWorker.Result.failure(), f.worker(SynchronizationScheduler.profileData(link)).doWork())
            }
            for (result in listOf(SubmissionHistoryResult.RateLimited, SubmissionHistoryResult.HttpFailure(503))) {
                f.remote.history = result
                assertEquals(ListenableWorker.Result.retry(), f.worker(SynchronizationScheduler.profileData(link)).doWork())
            }
            f.retryAfter.record("120")
            val calls = f.remote.historyCalls
            assertEquals(ListenableWorker.Result.retry(), f.worker(SynchronizationScheduler.catalogData()).doWork())
            assertEquals(0, f.remote.catalogCalls)
            // A new policy instance reads the same durable deadline after a process restart.
            assertTrue(CodeforcesRetryAfter(f.store, { now }).isDeferred())
            now += 120_000
            assertEquals(ListenableWorker.Result.success(), f.worker(SynchronizationScheduler.catalogData()).doWork())
            assertEquals(calls, f.remote.historyCalls)
        }
    }

    @Test fun `obsolete persisted worker cannot query new profile including A B A`() = runTest {
        Fixture(backgroundScope).use { f ->
            val old = f.link()
            f.local.saveProfile(LocalProfile("other", "Other", 1200, 1200, null, 0))
            f.local.saveProfile(LocalProfile("tourist", "Tourist", 1500, 1777, 1400, 0))
            assertNotEquals(old, f.local.profileLink.first())
            assertEquals(ListenableWorker.Result.success(), f.worker(SynchronizationScheduler.profileData(old)).doWork())
            assertEquals(0, f.remote.historyCalls)
            assertEquals(0, f.remote.userCalls)
            assertEquals(1777, f.local.profile.first()!!.loremRating)
        }
    }

    @Test fun `abandoned import recovers persisted page cursor and catches the head before readiness`() = runTest {
        Fixture(backgroundScope).use { f ->
            val link = f.link()
            val key = syncKey(SyncDataset.HISTORY, link)
            f.store.write(key, SynchronizationState(running = true, nextFrom = 501, importedCount = 1000))
            f.coordinator.recoverInterrupted()
            assertFalse(f.store.read(key).running)
            assertNotNull(f.store.read(key).failure)
            assertEquals(501, f.store.read(key).nextFrom)
            assertNull(f.store.read(key).lastSuccessEpochMillis)
            assertEquals(ListenableWorker.Result.success(), f.worker(SynchronizationScheduler.profileData(link)).doWork())
            assertEquals(2, f.remote.historyCalls) // resumed page, then head sweep
            assertEquals(now, f.local.profile.first()!!.lastSyncEpochMillis)
            assertEquals(1, f.store.read(key).nextFrom)
            assertFalse(f.background.profileDue(link))
            now += BackgroundSynchronization.QUARTER_HOUR
            assertTrue(f.background.profileDue(link))
            assertEquals(ListenableWorker.Result.success(), f.worker(SynchronizationScheduler.profileData(link)).doWork())
            assertEquals(1, f.remote.userCalls) // metadata remains valid for 24 hours
            now += BackgroundSynchronization.DAY
            f.worker(SynchronizationScheduler.profileData(link)).doWork()
            assertEquals(2, f.remote.userCalls)
        }
    }

    @Test fun `stopped worker propagates cancellation and retains a resumable import checkpoint`() = runTest {
        Fixture(backgroundScope).use { f ->
            val link = f.link()
            f.remote.history = SubmissionHistoryResult.Success((1500L downTo 1L).map {
                CodeforcesSubmission(it, ProblemId(it, "A"), "OK", Instant.ofEpochSecond(it))
            })
            val entered = CompletableDeferred<Unit>()
            f.remote.beforeHistory = {
                if (f.remote.historyCalls > 1) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            val worker = async { f.worker(SynchronizationScheduler.profileData(link)).doWork() }
            entered.await()
            worker.cancelAndJoin()
            assertTrue(worker.isCancelled)
            val state = f.store.read(syncKey(SyncDataset.HISTORY, link))
            assertEquals(501, state.nextFrom)
            assertFalse(state.running)
            assertNull(state.lastSuccessEpochMillis)
            assertEquals(0L, f.local.profile.first()!!.lastSyncEpochMillis)
            assertEquals(1000, f.local.problemHistory.first().size)
            f.remote.beforeHistory = {}
            assertEquals(ListenableWorker.Result.success(), f.worker(SynchronizationScheduler.profileData(link)).doWork())
            assertEquals(1500, f.local.problemHistory.first().size)
            assertEquals(now, f.local.profile.first()!!.lastSyncEpochMillis)
        }
    }

    @Test fun `stopping one caller preserves an import still shared by another caller`() = runTest {
        Fixture(backgroundScope).use { f ->
            f.link()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            f.remote.beforeHistory = { entered.complete(Unit); release.await() }
            val worker = async { f.coordinator.synchronizeHistory() }
            entered.await()
            val foreground = async { f.coordinator.synchronizeHistory() }
            runCurrent()
            worker.cancelAndJoin()
            assertTrue(f.store.read(syncKey(SyncDataset.HISTORY, f.local.profileLink.first())).running)
            release.complete(Unit)
            assertTrue(foreground.await() is dev.lorem.app.domain.ProblemHistorySyncResult.Success)
            assertEquals(1, f.remote.historyCalls)
            assertEquals(now, f.local.profile.first()!!.lastSyncEpochMillis)
        }
    }

    @Test fun `atomic recommendation rejects a removed and reconnected profile even with the same handle`() = runTest {
        Fixture(backgroundScope).use { f ->
            val old = f.link()
            f.local.clearProfile()
            f.link()
            val problem = CodeforcesProblem(ProblemId(100, "A"), "Problem", 1200, setOf("dp"))
            val ipsum = Ipsum(0, "tourist", problem, 1234, IpsumCategory.CURRENT_LEVEL,
                1200, 1300, 1200, 0, 1000, status = IpsumStatus.ACTIVE)
            assertTrue(runCatching { f.local.createActiveIpsumForLink(ipsum, old) }.exceptionOrNull() is ProfileLinkChanged)
            assertNull(f.local.activeIpsum.first())
        }
    }

    @Test fun `background upsolve changes pending classification once and keeps original rating`() = runTest {
        Fixture(backgroundScope).use { f ->
            val link = f.link()
            val problem = CodeforcesProblem(ProblemId(100, "A"), "Problem", 1200, setOf("dp"))
            val ipsum = f.local.createActiveIpsum(Ipsum(0, "tourist", problem, 1234, IpsumCategory.CURRENT_LEVEL,
                1200, 1300, 1200, 0, 1000, status = IpsumStatus.ACTIVE))
            f.local.endIpsumWithoutAc(ipsum.id, IpsumFailureReason.TIME_EXPIRED, 2000)
            val before = f.local.getIpsumResult(ipsum.id)!!.ipsum
            val rating = f.local.profile.first()!!.loremRating
            f.remote.history = SubmissionHistoryResult.Success(listOf(CodeforcesSubmission(1, problem.id, "OK", Instant.ofEpochMilli(3000))))
            f.worker(SynchronizationScheduler.profileData(link)).doWork()
            now += BackgroundSynchronization.QUARTER_HOUR
            f.worker(SynchronizationScheduler.profileData(link)).doWork()
            val after = f.local.getIpsumResult(ipsum.id)!!.ipsum
            assertEquals(before.copy(status = IpsumStatus.SOLVED_OUTSIDE, solvedOutsideAtEpochMillis = 3000), after)
            assertEquals(rating, f.local.profile.first()!!.loremRating)
        }
    }

    @Test fun `work manager keeps cadence and constraints and cancels only obsolete profile work`() = runTest {
        Fixture(backgroundScope).use { f ->
            val executor = Executors.newFixedThreadPool(2)
            try {
                val configuration = Configuration.Builder().setWorkerFactory(f.factory).setExecutor(executor).build()
                WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
                val wm = WorkManager.getInstance(context)
                val scheduler = SynchronizationScheduler(wm, f.local, f.background, backgroundScope)
                scheduler.observeProfiles()
                val link = f.link() // saving the validated profile triggers preparation without a manual action
                withContext(Dispatchers.Default) {
                    withTimeout(10_000) {
                        wm.getWorkInfosForUniqueWorkFlow(SynchronizationScheduler.profileName(link)).first { it.isNotEmpty() }
                    }
                }
                // A fresh foreground recovery also finishes scheduling the two preparation jobs.
                scheduler.recover()
                val first = wm.getWorkInfosForUniqueWork(SynchronizationScheduler.profileName(link)).get().single()
                val global = wm.getWorkInfosForUniqueWork(SynchronizationScheduler.CATALOG_PERIODIC).get().single()
                assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
                assertEquals(NetworkType.CONNECTED, global.constraints.requiredNetworkType)
                assertTrue(global.constraints.requiresBatteryNotLow())
                assertFalse(first.constraints.requiresBatteryNotLow())
                assertEquals(BackgroundSynchronization.QUARTER_HOUR, first.periodicityInfo!!.repeatIntervalMillis)
                assertEquals(BackgroundSynchronization.DAY, global.periodicityInfo!!.repeatIntervalMillis)
                scheduler.recover()
                assertEquals(first.id, wm.getWorkInfosForUniqueWork(SynchronizationScheduler.profileName(link)).get().single().id)
                assertEquals(first.nextScheduleTimeMillis, wm.getWorkInfosForUniqueWork(SynchronizationScheduler.profileName(link)).get().single().nextScheduleTimeMillis)
                // Constraints keep work enqueued until work-testing explicitly satisfies them.
                assertEquals(WorkInfo.State.ENQUEUED, first.state)
                val initial = wm.getWorkInfosForUniqueWork("prepare:${SynchronizationScheduler.ownerTag(link)}").get().single()
                val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
                driver.setAllConstraintsMet(initial.id)
                withContext(Dispatchers.Default) {
                    withTimeout(10_000) { wm.getWorkInfoByIdFlow(initial.id).first { it?.state?.isFinished == true } }
                }
                assertEquals(WorkInfo.State.SUCCEEDED, wm.getWorkInfoById(initial.id).get()!!.state)
                assertEquals(now, f.local.profile.first()!!.lastSyncEpochMillis)
                val spec = (wm as androidx.work.impl.WorkManagerImpl).workDatabase.workSpecDao().getWorkSpec(first.id.toString())!!
                assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
                assertEquals(30_000L, spec.backoffDelayDuration)
                // An old persisted owner is cancelled even with a freshly reconstructed scheduler.
                f.local.saveProfile(LocalProfile("other", "Other", 1200, 1200, null, 0))
                SynchronizationScheduler(wm, f.local, f.background, backgroundScope).recover()
                assertEquals(WorkInfo.State.CANCELLED, wm.getWorkInfoById(first.id).get()!!.state)
                assertEquals(global.id, wm.getWorkInfosForUniqueWork(SynchronizationScheduler.CATALOG_PERIODIC).get().single().id)
                val newLink = f.local.profileLink.first()!!
                f.local.clearProfile()
                scheduler.recover()
                assertEquals(WorkInfo.State.CANCELLED, wm.getWorkInfosForUniqueWork(SynchronizationScheduler.profileName(newLink)).get().single().state)
            } finally {
                WorkManagerTestInitHelper.closeWorkDatabase()
                executor.shutdownNow()
            }
        }
    }
}
