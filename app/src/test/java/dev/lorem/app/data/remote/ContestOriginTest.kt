package dev.lorem.app.data.remote

import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.ProblemCatalogResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContestOriginTest {
    private val problems = """{"status":"OK","result":{"problems":[{"contestId":1,"index":"A","name":"Example","rating":1200,"tags":[]}]}}"""
    private fun metadata(vararg contests: String) = """{"status":"OK","result":[${contests.joinToString(",") }]}"""
    private fun contest(start: String = "1593561600") = """{"id":1,"phase":"FINISHED","startTimeSeconds":$start}"""
    private val gate = object : CodeforcesRequestGate {
        override suspend fun <T> execute(request: suspend () -> T): T = request()
    }
    private fun remote(body: String) = CodeforcesApiRepository(gate, CodeforcesHttpClient { url ->
        HttpResponse(200, if (url.contains("contest.list")) body else problems)
    }, nowMillis = { 1_700_000_000_000L })

    @Test fun `official UTC date attached to matching origin and serialized as evidence`() = runTest {
        val value = (remote(metadata(contest())).problems() as ProblemCatalogResult.Success).problems.single()
        assertEquals(VerifiedProblemOrigin.CUTOFF_EPOCH_MILLIS, value.origin!!.startedAtEpochMillis)
        assertEquals(VerifiedProblemOrigin.SOURCE, value.origin!!.source)
        assertTrue(value.isEligibleForNewRecommendation())
    }

    @Test fun `missing ambiguous malformed dates never become eligible`() = runTest {
        for (body in listOf(metadata(), metadata(contest("1593561599")), metadata(contest(), contest("1600000000")),
            metadata("""{"id":1,"phase":"FINISHED"}"""), metadata(contest("\"1593561600\"")),
            metadata(contest("1593561600.5")), metadata(contest("9223372036854775807")))) {
            val value = (remote(body).problems() as ProblemCatalogResult.Success).problems.single()
            assertFalse(body, value.isEligibleForNewRecommendation())
        }
        assertEquals(ProblemCatalogResult.InvalidResponse, remote("bad json").problems())
        assertEquals(ProblemCatalogResult.InvalidResponse, remote(metadata("""{"id":"1"}""")).problems())
    }

    @Test fun `partial metadata failure returns failure and each request uses gate`() = runTest {
        var gates = 0
        val counting = object : CodeforcesRequestGate {
            override suspend fun <T> execute(request: suspend () -> T): T { gates++; return request() }
        }
        for (failure in listOf(429, 503)) {
            val result = CodeforcesApiRepository(counting, CodeforcesHttpClient { url ->
                if (url.contains("contest.list")) HttpResponse(failure, "") else HttpResponse(200, problems)
            }).problems()
            assertEquals(if (failure == 429) ProblemCatalogResult.RateLimited else ProblemCatalogResult.HttpFailure(503), result)
        }
        assertEquals(4, gates)
        val offline = CodeforcesApiRepository(counting, CodeforcesHttpClient { url ->
            if (url.contains("contest.list")) throw IOException("offline") else HttpResponse(200, problems)
        }).problems()
        assertEquals(ProblemCatalogResult.NetworkFailure, offline)
        assertEquals(6, gates)
    }
}
