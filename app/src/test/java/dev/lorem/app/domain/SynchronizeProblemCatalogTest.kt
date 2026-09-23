package dev.lorem.app.domain

import dev.lorem.app.data.fake.FakeLoremRepository
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.ProblemCatalogResult
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SynchronizeProblemCatalogTest {
    @Test
    fun `synchronization stores only rated unique candidates and update time`() = runTest {
        val local = FakeLoremRepository()
        val remote = CatalogRepository(
            ProblemCatalogResult.Success(
                listOf(problem(1, "A", 800), problem(1, "A", 900), problem(2, "B", null)),
            ),
        )

        val result = SynchronizeProblemCatalog(local, remote) { 1234L }()

        assertEquals(ProblemCatalogSyncResult.Success(1, 1234L), result)
        assertEquals(listOf(problem(1, "A", 800)), local.problemCatalog.first())
        assertEquals(1234L, local.catalogLastUpdatedEpochMillis.first())
    }

    @Test
    fun `refresh replaces snapshot without duplicates`() = runTest {
        val local = FakeLoremRepository()
        local.replaceProblemCatalog(listOf(problem(1, "A", 800), problem(2, "B", 900)), 10L)
        val remote = CatalogRepository(ProblemCatalogResult.Success(listOf(problem(2, "B", 1000))))

        SynchronizeProblemCatalog(local, remote) { 20L }()

        assertEquals(listOf(problem(2, "B", 1000)), local.problemCatalog.first())
        assertEquals(20L, local.catalogLastUpdatedEpochMillis.first())
    }

    @Test
    fun `network failure preserves and reports saved catalog`() = runTest {
        val local = FakeLoremRepository()
        val saved = problem(7, "C", 1200)
        local.replaceProblemCatalog(listOf(saved), 10L)

        val result = SynchronizeProblemCatalog(
            local,
            CatalogRepository(ProblemCatalogResult.NetworkFailure),
        ) { 20L }()

        assertEquals(ProblemCatalogSyncResult.NetworkFailure(1), result)
        assertEquals(listOf(saved), local.problemCatalog.first())
        assertEquals(10L, local.catalogLastUpdatedEpochMillis.first())
    }

    private fun problem(contestId: Long, index: String, rating: Int?) = CodeforcesProblem(
        ProblemId(contestId, index), "Problem $contestId$index", rating, setOf("math"),
    )

    private class CatalogRepository(private val result: ProblemCatalogResult) : CodeforcesRepository {
        override suspend fun user(handle: String): UserLookupResult = error("Not used")
        override suspend fun submissionHistory(handle: String): SubmissionHistoryResult = error("Not used")
        override suspend fun problems(): ProblemCatalogResult = result
    }
}
