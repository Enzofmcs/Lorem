package dev.lorem.app.data.remote

import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.ProblemCatalogResult
import dev.lorem.app.domain.repository.UserLookupResult
import java.io.IOException
import java.time.Instant
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
    fun `problem catalog parses identity name optional rating and official tags`() = runTest {
        val body = """{"status":"OK","result":{"problems":[{"contestId":4,"index":"A","name":"Watermelon","rating":800,"tags":["brute force","math"]},{"contestId":5,"index":"B","name":"Unrated","tags":["graphs"]}]}}"""

        val result = repository(body).problems() as ProblemCatalogResult.Success

        assertEquals(2, result.problems.size)
        assertEquals("4A", result.problems[0].id.toString())
        assertEquals("Watermelon", result.problems[0].name)
        assertEquals(800, result.problems[0].rating)
        assertEquals(setOf("brute force", "math"), result.problems[0].tags)
        assertEquals(null, result.problems[1].rating)
    }

    @Test
    fun `problem catalog ignores entries without stable identity`() = runTest {
        val body = """{"status":"OK","result":{"problems":[{"name":"No identity","rating":900,"tags":[]},{"contestId":10,"index":"C","name":"Valid","rating":1000,"tags":[]}]}}"""

        val result = repository(body).problems() as ProblemCatalogResult.Success

        assertEquals(listOf("10C"), result.problems.map { it.id.toString() })
    }

    @Test
    fun `problem catalog exposes failed invalid http and network responses`() = runTest {
        assertEquals(
            ProblemCatalogResult.Failed("maintenance"),
            repository("""{"status":"FAILED","comment":"maintenance"}""").problems(),
        )
        assertEquals(ProblemCatalogResult.InvalidResponse, repository("invalid").problems())
        assertEquals(
            ProblemCatalogResult.HttpFailure(503),
            CodeforcesApiRepository(immediateGate, CodeforcesHttpClient { HttpResponse(503, "") }).problems(),
        )
        assertEquals(
            ProblemCatalogResult.NetworkFailure,
            CodeforcesApiRepository(immediateGate, CodeforcesHttpClient { throw IOException("offline") }).problems(),
        )
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
    fun `rate limit and network failure are distinct for user lookup`() = runTest {
        val limited = CodeforcesApiRepository(immediateGate, CodeforcesHttpClient {
            HttpResponse(429, "")
        })
        val offline = CodeforcesApiRepository(immediateGate, CodeforcesHttpClient {
            throw IOException("offline")
        })
        assertEquals(UserLookupResult.RateLimited, limited.user("x"))
        assertEquals(UserLookupResult.NetworkFailure, offline.user("x"))
    }

    @Test
    fun `single incomplete page returns submissions and request parameters`() = runTest {
        val client = RecordingHttpClient(
            HttpResponse(200, ok(submission(10, 100, "A", "OK", 1_700_000_000))),
        )

        val result = historyRepository(client, pageSize = 2).submissionHistory("tourist")
            as SubmissionHistoryResult.Success

        assertEquals(1, result.submissions.size)
        assertEquals(10L, result.submissions.single().id)
        assertEquals(100L, result.submissions.single().problemId.contestId)
        assertEquals("A", result.submissions.single().problemId.index)
        assertEquals("OK", result.submissions.single().verdict)
        assertEquals(Instant.ofEpochSecond(1_700_000_000), result.submissions.single().createdAt)
        assertEquals(
            listOf("https://codeforces.com/api/user.status?handle=tourist&from=1&count=2"),
            client.urls,
        )
    }

    @Test
    fun `multiple pages advance from by requested count and stop at incomplete page`() = runTest {
        val client = RecordingHttpClient(
            HttpResponse(200, ok(submission(1, 10, "A"), submission(2, 20, "B"))),
            HttpResponse(200, ok(submission(3, 30, "C"))),
        )

        val result = historyRepository(client, pageSize = 2).submissionHistory("tourist")
            as SubmissionHistoryResult.Success

        assertEquals(listOf(1L, 2L, 3L), result.submissions.map { it.id })
        assertEquals(listOf(1, 3), client.urls.map { queryParameter(it, "from").toInt() })
        assertEquals(listOf(2, 2), client.urls.map { queryParameter(it, "count").toInt() })
    }

    @Test
    fun `full last page requests and accepts an empty terminating page`() = runTest {
        val client = RecordingHttpClient(
            HttpResponse(200, ok(submission(1, 10, "A"), submission(2, 20, "B"))),
            HttpResponse(200, ok()),
        )

        val result = historyRepository(client, pageSize = 2).submissionHistory("tourist")
            as SubmissionHistoryResult.Success

        assertEquals(listOf(1L, 2L), result.submissions.map { it.id })
        assertEquals(listOf(1, 3), client.urls.map { queryParameter(it, "from").toInt() })
    }

    @Test
    fun `handle is escaped in every history request`() = runTest {
        val client = RecordingHttpClient(HttpResponse(200, ok()))

        historyRepository(client, pageSize = 2).submissionHistory(" a+b c ")

        assertEquals("a%2Bb+c", queryParameter(client.urls.single(), "handle"))
    }

    @Test
    fun `two submissions for the same problem are preserved`() = runTest {
        val client = RecordingHttpClient(
            HttpResponse(
                200,
                ok(
                    submission(1, 4, "A", "WRONG_ANSWER"),
                    submission(2, 4, "A", "OK"),
                ),
            ),
        )

        val result = historyRepository(client, pageSize = 3).submissionHistory("ada")
            as SubmissionHistoryResult.Success

        assertEquals(2, result.submissions.size)
        assertEquals(result.submissions[0].problemId, result.submissions[1].problemId)
        assertEquals(listOf("WRONG_ANSWER", "OK"), result.submissions.map { it.verdict })
    }

    @Test
    fun `submissions without contest and index identity are ignored`() = runTest {
        val invalid = """{"id":3,"creationTimeSeconds":1700000000,"problem":{"name":"Only a name"}}"""
        val client = RecordingHttpClient(
            HttpResponse(200, ok(invalid, submission(4, 5, "B"))),
        )

        val result = historyRepository(client, pageSize = 3).submissionHistory("ada")
            as SubmissionHistoryResult.Success

        assertEquals(listOf(4L), result.submissions.map { it.id })
    }

    @Test
    fun `failed response does not return history accumulated from prior pages`() = runTest {
        val client = RecordingHttpClient(
            HttpResponse(200, ok(submission(1, 10, "A"))),
            HttpResponse(200, """{"status":"FAILED","comment":"bad handle"}"""),
        )

        val result = historyRepository(client, pageSize = 1).submissionHistory("x")

        assertEquals(SubmissionHistoryResult.Failed("bad handle"), result)
    }

    @Test
    fun `invalid json does not return history accumulated from prior pages`() = runTest {
        val client = RecordingHttpClient(
            HttpResponse(200, ok(submission(1, 10, "A"))),
            HttpResponse(200, "not-json"),
        )

        val result = historyRepository(client, pageSize = 1).submissionHistory("x")

        assertEquals(SubmissionHistoryResult.InvalidResponse, result)
    }

    @Test
    fun `http 429 has a typed result`() = runTest {
        val result = historyRepository(RecordingHttpClient(HttpResponse(429, "")))
            .submissionHistory("x")

        assertEquals(SubmissionHistoryResult.RateLimited, result)
    }

    @Test
    fun `other http errors have a typed result`() = runTest {
        val result = historyRepository(RecordingHttpClient(HttpResponse(503, "")))
            .submissionHistory("x")

        assertEquals(SubmissionHistoryResult.HttpFailure(503), result)
    }

    @Test
    fun `network failure does not return history accumulated from prior pages`() = runTest {
        val client = RecordingHttpClient(
            HttpResponse(200, ok(submission(1, 10, "A"))),
            IOException("offline"),
        )

        val result = historyRepository(client, pageSize = 1).submissionHistory("x")

        assertEquals(SubmissionHistoryResult.NetworkFailure, result)
    }

    private fun repository(body: String) = CodeforcesApiRepository(
        immediateGate,
        CodeforcesHttpClient { HttpResponse(200, body) },
    )

    private fun historyRepository(client: CodeforcesHttpClient, pageSize: Int = 2) =
        CodeforcesApiRepository(immediateGate, client, pageSize)

    private fun ok(vararg submissions: String): String =
        """{"status":"OK","result":[${submissions.joinToString(",")}]}"""

    private fun submission(
        id: Long,
        contestId: Long,
        index: String,
        verdict: String = "OK",
        creationTimeSeconds: Long = 1_700_000_000,
    ): String = """{"id":$id,"creationTimeSeconds":$creationTimeSeconds,"verdict":"$verdict","problem":{"contestId":$contestId,"index":"$index","name":"Ignored identity"}}"""

    private fun queryParameter(url: String, name: String): String = url.substringAfter("?$name=")
        .substringAfter("&$name=", missingDelimiterValue = url.substringAfter("?$name="))
        .substringBefore("&")

    private class RecordingHttpClient(vararg responses: Any) : CodeforcesHttpClient {
        private val responses = ArrayDeque(responses.toList())
        val urls = mutableListOf<String>()

        override suspend fun get(url: String): HttpResponse {
            urls += url
            return when (val response = responses.removeFirst()) {
                is HttpResponse -> response
                is IOException -> throw response
                else -> error("Unsupported response: $response")
            }
        }
    }
}
