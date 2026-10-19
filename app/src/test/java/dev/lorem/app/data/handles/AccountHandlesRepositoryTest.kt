package dev.lorem.app.data.handles

import dev.lorem.app.domain.handles.*
import dev.lorem.app.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AccountHandlesRepositoryTest {
    private class Remote : HandlesRemoteStore {
        var currentUid = "a"
        val profiles = mutableMapOf<Pair<String, String>, AccountHandle>()
        val snapshots = MutableStateFlow(HandlesSnapshot(serverConfirmed = true))
        var creates = 0
        override fun observe(uid: String) = snapshots
        override suspend fun find(uid: String, key: String): AccountHandle? {
            checkUid(uid); return profiles[uid to key]
        }
        override suspend fun create(uid: String, identity: HandleIdentity): AccountHandle {
            checkUid(uid); creates++
            return profiles.getOrPut(uid to identity.key) { AccountHandle(uid, identity, 1, 800, null, 0, 1234) }
        }
        override suspend fun select(uid: String, key: String, bindingVersion: Long) { checkUid(uid) }
        private fun checkUid(uid: String) { if (currentUid != uid) throw HandleException(HandleFailure.INVALID_SESSION) }
    }
    private class Cache : HandlesCache {
        val values = mutableMapOf<String, HandlesSnapshot>()
        var writes = 0
        override suspend fun read(uid: String) = values[uid] ?: HandlesSnapshot()
        override suspend fun save(uid: String, snapshot: HandlesSnapshot): HandlesSnapshot {
            check(snapshot.handles.all { it.uid == uid }); writes++; values[uid] = snapshot; return snapshot
        }
    }
    private class Cf : CodeforcesRepository {
        var calls = 0
        var result: UserLookupResult = UserLookupResult.Success(CodeforcesUser("Tourist", "Name", 3900))
        var before: suspend () -> Unit = {}
        override suspend fun user(handle: String): UserLookupResult { calls++; before(); return result }
        override suspend fun submissionHistory(handle: String): SubmissionHistoryResult = error("No history in story 35")
        override suspend fun problems(): ProblemCatalogResult = error("No catalog lookup in story 35")
    }

    @Test fun `canonical identity and initial 800 ignore official CF rating including unrated`() = runTest {
        val remote = Remote(); val cf = Cf(); val repository = AccountHandlesRepository(remote, cf, Cache())
        val high = repository.add("a", " TOURIST ")
        assertEquals("Tourist", high.identity.canonical); assertEquals("cf_tourist", high.identity.key)
        assertEquals(800, high.loremRating); assertNull(high.consolidatedRating)
        cf.result = UserLookupResult.Success(CodeforcesUser("Unrated", "Unrated", null))
        assertEquals(800, repository.add("a", "unrated").loremRating)
        remote.currentUid = "b"
        cf.result = UserLookupResult.Success(CodeforcesUser("TOURIST", "Name", 5000))
        assertEquals(800, repository.add("b", "Tourist").loremRating)
        assertEquals(3, remote.profiles.size)
    }

    @Test fun `existing binding preserves progress and consolidation without CF request or reset`() = runTest {
        val remote = Remote(); val cf = Cf(); val repository = AccountHandlesRepository(remote, cf, Cache())
        val original = repository.add("a", "tourist").copy(loremRating = 1377, consolidatedRating = 1200, progressVersion = 9)
        remote.profiles["a" to "cf_tourist"] = original
        cf.result = UserLookupResult.NetworkFailure
        assertEquals(original, repository.add("a", "TOURIST"))
        assertEquals(1, cf.calls); assertEquals(1, remote.creates)
    }

    @Test fun `invalid and missing handles never create and lookup failures stay explicit`() = runTest {
        val remote = Remote(); val cf = Cf(); val repository = AccountHandlesRepository(remote, cf, Cache())
        for (bad in listOf("", "a/b", "tourist;Petr", "name with spaces")) {
            assertFailure(HandleFailure.INVALID_HANDLE) { repository.add("a", bad) }
        }
        assertEquals(0, cf.calls)
        for ((result, failure) in listOf(
            UserLookupResult.UserNotFound to HandleFailure.NOT_FOUND,
            UserLookupResult.NetworkFailure to HandleFailure.NETWORK,
            UserLookupResult.RateLimited to HandleFailure.RATE_LIMITED,
            UserLookupResult.ApiFailure("error") to HandleFailure.CODEFORCES,
            UserLookupResult.Success(CodeforcesUser("Petr", "Different", 3000)) to HandleFailure.CODEFORCES,
        )) {
            cf.result = result
            assertFailure(failure) { repository.add("a", "tourist") }
        }
        assertEquals(0, remote.creates)
    }

    @Test fun `account changed during Codeforces request cannot create a binding in either account`() = runTest {
        val remote = Remote(); val cf = Cf().apply { before = { remote.currentUid = "b" } }
        val repository = AccountHandlesRepository(remote, cf, Cache())
        assertFailure(HandleFailure.INVALID_SESSION) { repository.add("a", "tourist") }
        assertTrue(remote.profiles.isEmpty())
    }

    @Test fun `only server-confirmed snapshots enter the uid cache and collectors are released`() = runTest {
        val remote = Remote(); val cache = Cache(); val repository = AccountHandlesRepository(remote, Cf(), cache)
        val profile = AccountHandle("a", HandleIdentity.parse("Tourist"), 1, 1377, 1200, 5, 1234)
        cache.values["a"] = HandlesSnapshot(listOf(profile))
        val received = mutableListOf<HandlesSnapshot>()
        remote.snapshots.value = HandlesSnapshot(serverConfirmed = false)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observe("a").toList(received) }
        assertEquals(1377, received.first().handles.single().loremRating)
        assertFalse(received.first().serverConfirmed); assertEquals(0, cache.writes)
        remote.snapshots.value = HandlesSnapshot(listOf(profile), serverConfirmed = true)
        runCurrent(); assertEquals(1, cache.writes); assertTrue(received.last().serverConfirmed)
        assertNull(cache.values["b"])
        collector.cancel(); runCurrent(); assertEquals(0, remote.snapshots.subscriptionCount.value)
    }

    @Test fun `normalization is locale independent and document key cannot become a path`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("cf_i_love_java", HandleIdentity.parse("I_love_Java").key)
            assertEquals("cf_..", HandleIdentity.parse("..").key)
        } finally { Locale.setDefault(previous) }
    }

    private suspend fun assertFailure(expected: HandleFailure, block: suspend () -> Unit) {
        try { block(); fail("Expected $expected") } catch (error: HandleException) { assertEquals(expected, error.failure) }
    }
}
