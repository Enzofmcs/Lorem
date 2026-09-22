package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesSubmission
import dev.lorem.app.domain.model.ProblemId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildProblemHistoryTest {
    @Test
    fun `accepted submission creates one attempted and solved problem`() {
        val history = buildProblemHistory(listOf(submission(1, 100, "A", "OK")))

        assertEquals(1, history.size)
        assertEquals(ProblemId(100, "A"), history.single().problemId)
        assertEquals(true, history.single().attempted)
        assertEquals(true, history.single().hasAcceptedSubmission)
    }

    @Test
    fun `wrong answer creates one attempted problem without accepted submission`() {
        val history = buildProblemHistory(listOf(submission(1, 100, "A", "WRONG_ANSWER")))

        assertEquals(true, history.single().attempted)
        assertEquals(false, history.single().hasAcceptedSubmission)
    }

    @Test
    fun `null verdict remains attempted without accepted submission`() {
        val history = buildProblemHistory(listOf(submission(1, 100, "A", null)))

        assertEquals(true, history.single().attempted)
        assertEquals(false, history.single().hasAcceptedSubmission)
    }

    @Test
    fun `wrong answer followed by accepted is one solved problem`() {
        val problemId = ProblemId(100, "A")

        val history = buildProblemHistory(
            listOf(
                submission(1, problemId, "WRONG_ANSWER"),
                submission(2, problemId, "OK"),
            ),
        )

        assertEquals(1, history.size)
        assertEquals(problemId, history.single().problemId)
        assertEquals(true, history.single().attempted)
        assertEquals(true, history.single().hasAcceptedSubmission)
    }

    @Test
    fun `same index in different contests creates different history entries`() {
        val history = buildProblemHistory(
            listOf(
                submission(1, 100, "A", "OK"),
                submission(2, 200, "A", "WRONG_ANSWER"),
            ),
        )

        assertEquals(listOf(ProblemId(100, "A"), ProblemId(200, "A")), history.map { it.problemId })
        assertEquals(listOf(true, false), history.map { it.hasAcceptedSubmission })
    }

    private fun submission(
        id: Long,
        contestId: Long,
        index: String,
        verdict: String?,
    ): CodeforcesSubmission = submission(id, ProblemId(contestId, index), verdict)

    private fun submission(
        id: Long,
        problemId: ProblemId,
        verdict: String?,
    ) = CodeforcesSubmission(
        id = id,
        problemId = problemId,
        verdict = verdict,
        createdAt = Instant.EPOCH.plusSeconds(id),
    )
}
