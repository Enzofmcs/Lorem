package dev.lorem.app.data.fake

import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.ProblemCatalogResult
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeCodeforcesRepositoryTest {
    @Test
    fun `fake problem has stable contest and index identity`() = runTest {
        val problem = (FakeCodeforcesRepository().problems() as ProblemCatalogResult.Success).problems.single()

        assertEquals(4L, problem.id.contestId)
        assertEquals("A", problem.id.index)
        assertEquals("4A", problem.id.toString())
    }

    @Test
    fun `fake validates a handle`() = runTest {
        val result = FakeCodeforcesRepository().user("ada") as UserLookupResult.Success
        assertEquals("ada", result.user.handle)
    }

    @Test
    fun `fake submission history is deterministic and offline`() = runTest {
        val repository = FakeCodeforcesRepository()

        val first = repository.submissionHistory("ada") as SubmissionHistoryResult.Success
        val second = repository.submissionHistory("different") as SubmissionHistoryResult.Success

        assertEquals(first, second)
        assertEquals(listOf(1L, 2L), first.submissions.map { it.id })
        assertEquals(1, first.submissions.map { it.problemId }.distinct().size)
        assertEquals(listOf("WRONG_ANSWER", "OK"), first.submissions.map { it.verdict })
    }
}
