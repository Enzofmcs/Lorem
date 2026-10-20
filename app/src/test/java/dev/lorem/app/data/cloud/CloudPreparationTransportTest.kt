package dev.lorem.app.data.cloud

import dev.lorem.app.data.remote.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudPreparationTransportTest {
    @Test fun `controlled transport compares full starts with verified incremental warm start`() = runTest {
        var total = 6
        var time = 0L
        val calls = mutableListOf<Pair<String, Long>>()
        val transport = CodeforcesHttpClient { url ->
            val uri = URI(url); val method = uri.path.substringAfterLast('/')
            calls += method to time
            val result: Any = when (method) {
                "user.status" -> {
                    val query = uri.query.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
                    val from = query.getValue("from").toInt(); val count = query.getValue("count").toInt()
                    JSONArray((total downTo 1).drop(from - 1).take(count).map { n -> JSONObject()
                        .put("id", n).put("contestId", 2000 + n).put("creationTimeSeconds", n)
                        .put("verdict", "OK").put("problem", JSONObject().put("contestId", 2000 + n).put("index", "A").put("name", "Synthetic")) })
                }
                "problemset.problems" -> JSONObject().put("problems", JSONArray().put(JSONObject()
                    .put("contestId", 2000).put("index", "A").put("name", "Synthetic catalog").put("rating", 800).put("tags", JSONArray().put("math"))))
                "contest.list" -> JSONArray().put(JSONObject().put("id", 2000).put("startTimeSeconds", 1600000000))
                "user.info" -> JSONArray().put(JSONObject().put("handle", "tourist").put("rating", 1500))
                else -> error(method)
            }
            HttpResponse(200, JSONObject().put("status", "OK").put("result", result).toString())
        }
        val gate = TwoSecondCodeforcesRequestGate({ time }, { time += it })
        val api = CodeforcesApiRepository(gate, transport, submissionPageSize = 4)
        suspend fun baseline() { api.submissionHistory("tourist"); api.problems(); api.user("tourist") }
        baseline(); assertEquals(5, calls.size)
        total = 7; baseline(); assertEquals(10, calls.size)
        calls.clear(); total = 6
        val preparation = CloudPreparation(api, { time }, pageSize = 4)
        assertEquals(6, preparation.prepare("A:key:1", "tourist", {}) {}.history.size)
        assertEquals(14_000L, preparation.lastMetrics!!.elapsedMillis) // Simulated gate time only.
        assertEquals(7, calls.size) // overlap plus top verification adds cold-scan work, explicitly measured.
        val cold = calls.size
        total = 7
        assertEquals(7, preparation.prepare("A:key:1", "tourist", {}) {}.history.size)
        assertEquals(4_000L, preparation.lastMetrics!!.elapsedMillis) // No device/network latency claim.
        assertEquals(2, calls.size - cold)
        assertEquals(listOf("user.status", "user.info"), calls.drop(cold).map { it.first })
        assertTrue(calls.zipWithNext().all { (a, b) -> b.second - a.second >= 2000 })
        assertEquals(1, preparation.lastMetrics!!.pages)
        assertEquals(0, preparation.lastMetrics!!.catalogLoads)
        assertTrue(preparation.lastMetrics!!.completed)
    }
}
