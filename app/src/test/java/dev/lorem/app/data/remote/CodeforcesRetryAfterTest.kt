package dev.lorem.app.data.remote

import dev.lorem.app.domain.sync.MemorySynchronizationStore
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CodeforcesRetryAfterTest {
    @Test fun `HTTP Retry-After reaches the global gate and delays a different API dataset`() = runTest {
        var clock = 1_000_000L
        val policy = CodeforcesRetryAfter(MemorySynchronizationStore(), { clock }) { clock += it }
        val gate = TwoSecondCodeforcesRequestGate({ clock }, { clock += it }, policy::awaitPermission)
        val calls = mutableListOf<Long>()
        val remote = CodeforcesApiRepository(gate, CodeforcesHttpClient {
            calls += clock
            if (calls.size == 1) HttpResponse(429, "", "60") else HttpResponse(503, "")
        }, onRetryAfter = policy::record)
        assertEquals(dev.lorem.app.domain.repository.UserLookupResult.RateLimited, remote.user("tourist"))
        assertTrue(policy.isDeferred())
        assertEquals(dev.lorem.app.domain.repository.SubmissionHistoryResult.HttpFailure(503), remote.submissionPage("tourist", 1, 1000))
        assertEquals(listOf(1_000_000L, 1_060_000L), calls)
    }

    @Test fun `seconds and HTTP date survive reconstruction and preserve gate spacing`() = runTest {
        var clock = 1_000_000L
        val store = MemorySynchronizationStore()
        val policy = CodeforcesRetryAfter(store, { clock }) { clock += it }
        policy.record("120")
        val restored = CodeforcesRetryAfter(store, { clock }) { clock += it }
        val gate = TwoSecondCodeforcesRequestGate({ clock }, { clock += it }, restored::awaitPermission)
        val calls = mutableListOf<Long>()
        gate.execute { calls += clock }
        gate.execute { calls += clock }
        assertEquals(listOf(1_120_000L, 1_122_000L), calls)
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(clock + 60_000).atZone(ZoneOffset.UTC))
        restored.record(date)
        restored.record("1") // a shorter deadline cannot bypass the prior response
        gate.execute { calls += clock }
        assertEquals(1_182_000L, calls.last())
        restored.record("invalid")
        restored.record("-1")
        assertFalse(restored.isDeferred())
    }
}
