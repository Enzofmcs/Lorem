package dev.lorem.app.data.cloud

import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CloudPreparationTest {
    private class Cf : CodeforcesRepository {
        var rows = (12L downTo 1).map { CodeforcesSubmission(it, ProblemId(2000 + it, "A"), "OK", Instant.ofEpochMilli(it * 1000)) }
        var pages = 0; var catalogs = 0; var users = 0; var failPage = false; var complete = true
        override suspend fun submissionPage(handle: String, from: Int, count: Int): SubmissionHistoryResult {
            pages++
            if (failPage) { failPage = false; return SubmissionHistoryResult.NetworkFailure }
            return SubmissionHistoryResult.Success(rows.drop(from - 1).take(count), verificationComplete = complete)
        }
        override suspend fun submissionHistory(handle: String) = SubmissionHistoryResult.Success(rows)
        override suspend fun problems(): ProblemCatalogResult { catalogs++; return ProblemCatalogResult.Success(emptyList()) }
        override suspend fun user(handle: String): UserLookupResult { users++; return UserLookupResult.Success(CodeforcesUser(handle, handle, 1500)) }
    }
    @Test fun `cold preparation resumes within page budgets then updates incrementally and reuses catalog`() = runTest {
        val cf = Cf(); var clock = 0L
        val preparation = CloudPreparation(cf, { clock }, pageSize = 4, pageBudget = 3)
        var prepared: CloudPreparation.Prepared? = null
        val stages = mutableListOf<CloudPreparationStage>()
        repeat(6) {
            if (prepared == null) {
                try { prepared = preparation.prepare("A:cf_tourist:1", "tourist", {}) { stages += it.stage } }
                catch (_: IllegalStateException) { assertFalse(preparation.lastMetrics!!.completed) }
                assertTrue(preparation.lastMetrics!!.pages <= 3)
            }
        }
        assertEquals(cf.rows.toSet(), prepared!!.history.toSet())
        assertEquals(1, cf.catalogs); assertEquals(1, cf.users)
        val before = cf.pages
        cf.rows = listOf(CodeforcesSubmission(13, ProblemId(2013, "A"), "WRONG_ANSWER", Instant.ofEpochMilli(13000))) + cf.rows
        val next = preparation.prepare("A:cf_tourist:1", "tourist", {}) {}
        assertEquals(13, next.history.size); assertEquals(1, cf.pages - before)
        assertEquals(1, cf.catalogs); assertEquals(2, cf.users)
        assertTrue(preparation.lastMetrics!!.incremental)
        assertEquals(listOf(CloudPreparationStage.CATALOG, CloudPreparationStage.PROFILE), stages.takeLast(2))
        clock += CloudPreparation.CACHE_MILLIS + 1
        try { preparation.prepare("A:cf_tourist:1", "tourist", {}) {}; fail("Must rebuild stale history") }
        catch (_: IllegalStateException) { assertFalse(preparation.lastMetrics!!.incremental) }
    }
    @Test fun `incomplete failed and changed context never authorize from cached coverage`() = runTest {
        val cf = Cf().apply { rows = rows.take(2) }
        val preparation = CloudPreparation(cf, { 0 }, 4, 3)
        preparation.prepare("A:key:1", "tourist", {}) {}
        cf.complete = false
        try { preparation.prepare("A:key:1", "tourist", {}) {}; fail("Incomplete page") } catch (_: IllegalStateException) {}
        assertEquals(1, cf.users)
        cf.complete = true; cf.failPage = true
        try { preparation.prepare("B:key:1", "tourist", {}) {}; fail("Other account must verify") } catch (_: IllegalStateException) {}
        val result = preparation.prepare("B:key:1", "tourist", {}) {}
        assertEquals(2, result.history.size)
        assertFalse(preparation.lastMetrics!!.incremental)
    }
    @Test fun `pending window is bounded and response arriving after session change is discarded`() = runTest {
        val cf = Cf()
        val preparation = CloudPreparation(cf, { 0 }, 2, 3)
        assertNull(preparation.window("tourist", 0) {})
        assertEquals(4, cf.pages)
        var window: List<CodeforcesSubmission>? = null
        repeat(4) { if (window == null) window = preparation.window("tourist", 0) {} }
        assertEquals(cf.rows.toSet(), window!!.toSet())
        var validations = 0
        try { preparation.prepare("A:key:1", "tourist", { check(++validations < 2) { "Session changed" } }) {}; fail("Late response") }
        catch (_: IllegalStateException) { assertEquals(0, cf.users) }
    }
}
