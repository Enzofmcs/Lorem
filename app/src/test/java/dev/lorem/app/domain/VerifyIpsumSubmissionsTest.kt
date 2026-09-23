package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.CodeforcesSubmission
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.IpsumSubmission
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.IpsumUpdate
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.ProblemCatalogResult
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.UserLookupResult
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyIpsumSubmissionsTest {
    @Test fun `filters old other-problem and judging submissions`() = runTest {
        val local = RecordingLoremRepository()
        val remote = Remote(listOf(
            submission(1, 999, "A", "WRONG_ANSWER", 2),
            submission(2, 100, "A", "WRONG_ANSWER", 0),
            submission(3, 100, "A", null, 2),
            submission(4, 100, "A", "TESTING", 2),
        ))

        val result = VerifyIpsumSubmissions(local, remote)() as VerifyIpsumResult.Success

        assertEquals(0, result.newSubmissionCount)
        assertTrue(local.recorded.isEmpty())
    }

    @Test fun `unordered WA other verdict AC and later error count only errors before first AC`() = runTest {
        val local = RecordingLoremRepository()
        val remote = Remote(listOf(
            submission(4, verdict = "TIME_LIMIT_EXCEEDED", second = 5),
            submission(3, verdict = "OK", second = 4),
            submission(2, verdict = "COMPILATION_ERROR", second = 3),
            submission(1, verdict = "WRONG_ANSWER", second = 2),
        ))

        val result = VerifyIpsumSubmissions(local, remote)() as VerifyIpsumResult.Success

        assertTrue(result.completed)
        assertEquals(2, result.errorCount)
        assertEquals(listOf(1L, 2L, 3L, 4L), local.recorded.map { it.submissionId })
        assertEquals(setOf("WRONG_ANSWER", "COMPILATION_ERROR", "OK", "TIME_LIMIT_EXCEEDED"), local.recorded.map { it.verdict }.toSet())
    }

    @Test fun `repeated checks deduplicate and complete only once`() = runTest {
        val local = RecordingLoremRepository()
        val remote = Remote(listOf(submission(1, verdict = "WRONG_ANSWER"), submission(2, verdict = "OK", second = 3)))
        val verify = VerifyIpsumSubmissions(local, remote)

        val first = verify() as VerifyIpsumResult.Success
        val second = verify()

        assertTrue(first.completed)
        assertEquals(1, local.completions)
        assertEquals(2, local.recorded.size)
        assertEquals(VerifyIpsumResult.NoActiveIpsum, second)
    }

    @Test fun `network failure does not touch persistence`() = runTest {
        val local = RecordingLoremRepository()
        val result = VerifyIpsumSubmissions(local, Remote(failure = SubmissionHistoryResult.NetworkFailure))()
        assertEquals(VerifyIpsumResult.NetworkFailure, result)
        assertTrue(local.recorded.isEmpty())
        assertFalse(local.completed)
    }

    private fun submission(id: Long, contest: Long = 100, index: String = "A", verdict: String? = "WRONG_ANSWER", second: Long = 2) =
        CodeforcesSubmission(id, ProblemId(contest, index), verdict, Instant.ofEpochSecond(second))
}

private class Remote(
    private val submissions: List<CodeforcesSubmission> = emptyList(),
    private val failure: SubmissionHistoryResult? = null,
) : CodeforcesRepository {
    override suspend fun user(handle: String) = UserLookupResult.NetworkFailure
    override suspend fun submissionHistory(handle: String) = failure ?: SubmissionHistoryResult.Success(submissions)
    override suspend fun problems() = ProblemCatalogResult.Success(emptyList())
}

private class RecordingLoremRepository : LoremRepository {
    override val profile = MutableStateFlow<LocalProfile?>(null)
    override val problemHistory = MutableStateFlow<List<ProblemHistory>>(emptyList())
    override val problemCatalog = MutableStateFlow<List<CodeforcesProblem>>(emptyList())
    override val catalogLastUpdatedEpochMillis = MutableStateFlow<Long?>(null)
    override val ipsums = MutableStateFlow<List<Ipsum>>(emptyList())
    override val activeIpsum = MutableStateFlow<Ipsum?>(ipsum())
    val recorded = mutableListOf<IpsumSubmission>()
    var completed = false
    var completions = 0
    override suspend fun saveProfile(profile: LocalProfile) = Unit
    override suspend fun clearProfile() = Unit
    override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) = Unit
    override suspend fun replaceProblemCatalog(problems: List<CodeforcesProblem>, updatedAtEpochMillis: Long) = Unit
    override suspend fun createActiveIpsum(ipsum: Ipsum) = ipsum
    override suspend fun revealIpsumHint(ipsumId: Long, revealedAtEpochMillis: Long) = Unit
    override suspend fun recordIpsumSubmissions(ipsumId: Long, submissions: List<IpsumSubmission>): IpsumUpdate {
        val new = submissions.filter { candidate -> recorded.none { it.submissionId == candidate.submissionId } }
        recorded += new
        val accepted = recorded.sortedWith(compareBy({ it.createdAtEpochMillis }, { it.submissionId })).firstOrNull { it.verdict == "OK" }
        val errors = recorded.count { it.verdict != "OK" && (accepted == null || it.createdAtEpochMillis < accepted.createdAtEpochMillis || it.createdAtEpochMillis == accepted.createdAtEpochMillis && it.submissionId < accepted.submissionId) }
        if (accepted != null && !completed) {
            completed = true
            completions++
            activeIpsum.value = null
        }
        return IpsumUpdate(new.size, errors, accepted != null)
    }
}

private fun ipsum() = Ipsum(
    id = 1, ownerHandle = "tourist",
    problem = CodeforcesProblem(ProblemId(100, "A"), "Problem", 800, emptySet()),
    initialLoremRating = 800, category = IpsumCategory.CURRENT_LEVEL,
    desiredRatingMin = 800, desiredRatingMax = 800, selectedRating = 800,
    fallbackDistance = 0, startedAtEpochMillis = 1_000, status = IpsumStatus.ACTIVE,
)
