package dev.lorem.app.data.fake

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import dev.lorem.app.domain.repository.UserLookupResult

class FakeCodeforcesRepositoryTest {
    @Test
    fun `fake problem has stable contest and index identity`() = runTest {
        val problem = FakeCodeforcesRepository().problems().single()

        assertEquals(4L, problem.id.contestId)
        assertEquals("A", problem.id.index)
        assertEquals("4A", problem.id.toString())
    }

    @Test
    fun `fake validates a handle`() = runTest {
        val result = FakeCodeforcesRepository().user("ada") as UserLookupResult.Success
        assertEquals("ada", result.user.handle)
    }
}
