package dev.lorem.app.domain.sync

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.data.local.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import dev.lorem.app.ui.MainDispatcherRule
import dev.lorem.app.ui.monitorResumedIpsum
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
@Config(sdk = [35], application = Application::class)
class IpsumSubmissionMonitorTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle = registry
    }

    private class ClockRepository : LoremRepository {
        override val profile = kotlinx.coroutines.flow.MutableStateFlow<LocalProfile?>(null)
        override val problemHistory = kotlinx.coroutines.flow.MutableStateFlow<List<ProblemHistory>>(emptyList())
        override val problemCatalog = kotlinx.coroutines.flow.MutableStateFlow<List<CodeforcesProblem>>(emptyList())
        override val catalogLastUpdatedEpochMillis = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
        override val ipsums = kotlinx.coroutines.flow.MutableStateFlow<List<Ipsum>>(emptyList())
        override val activeIpsum = kotlinx.coroutines.flow.MutableStateFlow<Ipsum?>(null)
        override suspend fun saveProfile(profile: LocalProfile) { this.profile.value = profile }
        override suspend fun clearProfile() { profile.value = null }
        override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) {}
        override suspend fun replaceProblemCatalog(problems: List<CodeforcesProblem>, updatedAtEpochMillis: Long) {}
        override suspend fun createActiveIpsum(ipsum: Ipsum): Ipsum = ipsum.copy(id = 1).also {
            activeIpsum.value = it; ipsums.value = listOf(it)
        }
        override suspend fun revealIpsumHint(ipsumId: Long, revealedAtEpochMillis: Long) {}
    }

    private inner class Fixture(scope: CoroutineScope, val clock: TestCoroutineScheduler, private val useDisk: Boolean = true) : AutoCloseable {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LoremDatabase::class.java).build()
        val prefs = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.newFile("${System.nanoTime()}.preferences_pb") })
        val repository: LoremRepository = if (useDisk) LocalLoremRepository(prefs, db.problemHistoryDao(), db.problemCatalogDao(), db.ipsumDao()) else ClockRepository()
        val store: SynchronizationStore = if (useDisk) PreferencesSynchronizationStore(prefs) else MemorySynchronizationStore()
        val calls = java.util.Collections.synchronizedList(mutableListOf<Long>())
        var result: SubmissionHistoryResult = SubmissionHistoryResult.Success(emptyList())
        var beforeResponse: suspend () -> Unit = {}
        val remote = object : CodeforcesRepository {
            override suspend fun user(handle: String) = UserLookupResult.Success(CodeforcesUser("tourist", "Tourist", 1500))
            override suspend fun problems() = ProblemCatalogResult.Success(emptyList())
            override suspend fun submissionHistory(handle: String): SubmissionHistoryResult {
                calls += clock.currentTime
                beforeResponse()
                return result
            }
        }
        val coordinator = SynchronizationCoordinator(repository, remote, scope, store, { 1_000_000 + clock.currentTime })
        val monitor = IpsumSubmissionMonitor(repository, coordinator)
        suspend fun start(): Ipsum {
            repository.saveProfile(LocalProfile("tourist", "Tourist", 1500, 1200, null, 1))
            val problem = CodeforcesProblem(ProblemId(100, "A"), "Problem", 1200, setOf("dp"))
            return repository.createActiveIpsum(Ipsum(0, "tourist", problem, 1200, IpsumCategory.CURRENT_LEVEL,
                1200, 1200, 1200, 0, 1000, status = IpsumStatus.ACTIVE))
        }
        suspend fun state(id: Long) = store.read(syncKey(SyncDataset.SUBMISSIONS, repository.profileLink.first(), id))
        suspend fun settled(id: Long, count: Int) {
            if (!useDisk) {
                assertTrue(calls.size >= count)
                assertFalse(state(id).running)
                return
            }
            withContext(Dispatchers.Default) {
                withTimeout(10_000) { while (calls.size < count || state(id).running) delay(1) }
            }
        }
        override fun close() = db.close()
    }

    @Test fun `only RESUMED polls immediately and every fifteen seconds and rotation does not multiply loops`() = runTest {
        Fixture(backgroundScope, testScheduler, useDisk = false).use { f ->
            val ipsum = f.start()
            val owner = Owner().also { it.registry.currentState = Lifecycle.State.CREATED }
            val binding = backgroundScope.launch { monitorResumedIpsum(owner.lifecycle, ipsum.id, f.monitor::monitor) }
            runCurrent()
            assertTrue(f.calls.isEmpty())
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertTrue(f.calls.isEmpty())
            owner.registry.currentState = Lifecycle.State.RESUMED
            runCurrent()
            f.settled(ipsum.id, 1)
            assertEquals(listOf(0L), f.calls)
            advanceTimeBy(15_000); runCurrent()
            f.settled(ipsum.id, 2)
            assertEquals(listOf(0L, 15_000L), f.calls)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            advanceTimeBy(60_000); runCurrent()
            assertEquals(2, f.calls.size)
            owner.registry.currentState = Lifecycle.State.RESUMED
            runCurrent()
            f.settled(ipsum.id, 3)
            assertEquals(75_000L, f.calls.last())
            binding.cancelAndJoin() // leaving the route or disposing its old composition
            val rotated = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
            val next = backgroundScope.launch { monitorResumedIpsum(rotated.lifecycle, ipsum.id, f.monitor::monitor) }
            runCurrent()
            f.settled(ipsum.id, 4)
            rotated.registry.currentState = Lifecycle.State.RESUMED // same lifecycle/recomposition
            advanceTimeBy(15_000); runCurrent()
            f.settled(ipsum.id, 5)
            assertEquals(listOf(0L, 15_000L, 75_000L, 75_000L, 90_000L), f.calls)
            next.cancelAndJoin()
            advanceTimeBy(60_000); runCurrent()
            assertEquals(5, f.calls.size)
        }
    }

    @Test fun `two visible bindings serialize a single loop and ending cancels an in flight request`() = runTest {
        Fixture(backgroundScope, testScheduler).use { f ->
            val ipsum = f.start()
            val entered = CompletableDeferred<Unit>()
            var cancelled = false
            f.beforeResponse = { entered.complete(Unit); try { awaitCancellation() } finally { cancelled = true } }
            val one = backgroundScope.launch { f.monitor.monitor(ipsum.id) }
            entered.await()
            val two = backgroundScope.launch { f.monitor.monitor(ipsum.id) }
            runCurrent()
            assertEquals(1, f.calls.size)
            f.repository.endIpsumWithoutAc(ipsum.id, IpsumFailureReason.TIME_EXPIRED, 5000)
            runCurrent()
            one.join(); two.join()
            assertTrue(cancelled)
            assertFalse(f.state(ipsum.id).running)
            assertEquals(SyncFailureKind.INTERRUPTED, f.state(ipsum.id).failureKind)
            assertNull(f.state(ipsum.id).lastSuccessEpochMillis)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(1, f.calls.size)
        }
    }

    @Test fun `failures back off without changing local attempt and recovery restores fifteen seconds`() = runTest {
        Fixture(backgroundScope, testScheduler, useDisk = false).use { f ->
            val ipsum = f.start()
            f.result = SubmissionHistoryResult.NetworkFailure
            val session = backgroundScope.launch { f.monitor.monitor(ipsum.id) }
            runCurrent()
            f.settled(ipsum.id, 1)
            assertEquals(SyncFailureKind.NETWORK, f.state(ipsum.id).failureKind)
            advanceTimeBy(30_000); runCurrent(); f.settled(ipsum.id, 2)
            advanceTimeBy(60_000); runCurrent(); f.settled(ipsum.id, 3)
            advanceTimeBy(120_000); runCurrent(); f.settled(ipsum.id, 4)
            assertEquals(listOf(0L, 30_000L, 90_000L, 210_000L), f.calls)
            assertEquals(ipsum, f.repository.activeIpsum.first())
            f.result = SubmissionHistoryResult.Success(emptyList())
            advanceTimeBy(120_000); runCurrent()
            f.settled(ipsum.id, 5)
            assertEquals(330_000L, f.calls.last())
            assertNull(f.state(ipsum.id).failure)
            assertNotNull(f.state(ipsum.id).lastSuccessEpochMillis)
            advanceTimeBy(15_000); runCurrent()
            f.settled(ipsum.id, 6)
            assertEquals(345_000L, f.calls.last())
            session.cancelAndJoin()
        }
    }

    @Test fun `automatic WA TESTING AC deduplicates ids ignores older submissions and applies rating once`() = runTest {
        Fixture(backgroundScope, testScheduler).use { f ->
            val ipsum = f.start()
            f.repository.revealIpsumHint(ipsum.id, 1500)
            val old = CodeforcesSubmission(1, ipsum.problem.id, "OK", Instant.ofEpochMilli(500))
            val wa = CodeforcesSubmission(2, ipsum.problem.id, "WRONG_ANSWER", Instant.ofEpochMilli(2000))
            val testing = CodeforcesSubmission(3, ipsum.problem.id, "TESTING", Instant.ofEpochMilli(3000))
            f.result = SubmissionHistoryResult.Success(listOf(old, wa, wa, testing,
                CodeforcesSubmission(4, ProblemId(101, "A"), "OK", Instant.ofEpochMilli(2000))))
            val session = backgroundScope.launch { f.monitor.monitor(ipsum.id) }
            runCurrent()
            f.settled(ipsum.id, 1)
            assertEquals(1, f.repository.activeIpsum.first()!!.errorCount)
            assertEquals(1200, f.repository.profile.first()!!.loremRating)
            advanceTimeBy(15_000); runCurrent()
            f.settled(ipsum.id, 2)
            assertNotNull(f.repository.activeIpsum.first())
            f.result = SubmissionHistoryResult.Success(listOf(wa, testing.copy(verdict = "OK"), wa,
                CodeforcesSubmission(5, ipsum.problem.id, "WRONG_ANSWER", Instant.ofEpochMilli(4000))))
            advanceTimeBy(15_000); runCurrent()
            session.join()
            val result = f.repository.getIpsumResult(ipsum.id)!!.ipsum
            assertEquals(IpsumStatus.COMPLETED, result.status)
            assertEquals(3000L, result.endedAtEpochMillis)
            assertEquals(1, result.errorCount)
            assertEquals(1500L, result.hintRevealedAtEpochMillis)
            val rating = f.repository.profile.first()!!.loremRating
            assertEquals(result.finalLoremRating, rating)
            assertNotNull(f.state(ipsum.id).lastSuccessEpochMillis)
            assertNull(f.state(ipsum.id).failure)
            assertFalse(f.repository.endIpsumWithoutAc(ipsum.id, IpsumFailureReason.TIME_EXPIRED, 6000))
            f.coordinator.verifyActiveIpsum()
            assertEquals(result, f.repository.getIpsumResult(ipsum.id)!!.ipsum)
            assertEquals(rating, f.repository.profile.first()!!.loremRating)
            val completedCalls = f.calls.size
            advanceTimeBy(60_000); runCurrent()
            assertEquals(completedCalls, f.calls.size)
        }
    }

    @Test fun `worker shares polling request and manual closure preserves original rating before upsolve`() = runTest {
        Fixture(backgroundScope, testScheduler).use { f ->
            val ipsum = f.start()
            val link = f.repository.profileLink.first()!!
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            f.result = SubmissionHistoryResult.Success(listOf(CodeforcesSubmission(1, ipsum.problem.id, "OK", Instant.ofEpochMilli(5000))))
            f.beforeResponse = { entered.complete(Unit); release.await() }
            val polling = backgroundScope.launch { f.monitor.monitor(ipsum.id) }
            entered.await()
            val worker = async { BackgroundSynchronization(f.repository, f.coordinator).profile(link) }
            runCurrent()
            assertEquals(1, f.calls.size)
            f.repository.endIpsumWithoutAc(ipsum.id, IpsumFailureReason.TIME_EXPIRED, 4000)
            val original = f.repository.getIpsumResult(ipsum.id)!!.ipsum
            polling.join()
            f.beforeResponse = {}
            release.complete(Unit)
            assertEquals(SyncWorkResult.SUCCESS, worker.await())
            val upsolved = f.repository.getIpsumResult(ipsum.id)!!.ipsum
            assertEquals(original.copy(status = IpsumStatus.SOLVED_OUTSIDE, solvedOutsideAtEpochMillis = 5000), upsolved)
            assertEquals(original.finalLoremRating, f.repository.profile.first()!!.loremRating)
        }
    }
}
