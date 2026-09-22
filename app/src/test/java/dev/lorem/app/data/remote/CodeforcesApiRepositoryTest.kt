package dev.lorem.app.data.remote

import dev.lorem.app.domain.repository.UserLookupResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CodeforcesApiRepositoryTest {
    private val immediateGate = object : CodeforcesRequestGate {
        override suspend fun <T> execute(request: suspend () -> T): T = request()
    }

    @Test
    fun `valid handle returns identification and optional rating`() = runTest {
        val repository = repository(
            """{"status":"OK","result":[{"handle":"tourist","firstName":"Gennady","lastName":"Korotkevich","rating":4009}]}""",
        )
        val result = repository.user("tourist") as UserLookupResult.Success
        assertEquals("Gennady Korotkevich", result.user.displayName)
        assertEquals(4009, result.user.rating)
    }

    @Test
    fun `missing handle is distinct from other failed response`() = runTest {
        assertEquals(
            UserLookupResult.UserNotFound,
            repository("""{"status":"FAILED","comment":"handles: User with handle x not found"}""")
                .user("x"),
        )
        assertTrue(
            repository("""{"status":"FAILED","comment":"Internal error"}""").user("x")
                is UserLookupResult.ApiFailure,
        )
    }

    @Test
    fun `rate limit and network failure are distinct`() = runTest {
        val limited = CodeforcesApiRepository(immediateGate, CodeforcesHttpClient {
            HttpResponse(429, "")
        })
        val offline = CodeforcesApiRepository(immediateGate, CodeforcesHttpClient {
            throw IOException("offline")
        })
        assertEquals(UserLookupResult.RateLimited, limited.user("x"))
        assertEquals(UserLookupResult.NetworkFailure, offline.user("x"))
    }

    private fun repository(body: String) = CodeforcesApiRepository(
        immediateGate,
        CodeforcesHttpClient { HttpResponse(200, body) },
    )
}
