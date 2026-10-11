package dev.lorem.app.domain

import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.repository.*
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePendingIpsumsTest {
    @Test
    fun `moves only exact accepted submissions strictly after each pending Ipsum and keeps first AC`() = runTest {
        val pending = ipsum(1, ProblemId(100, "A"), start = 1_000, end = 2_000)
        val other = ipsum(2, ProblemId(100, "B"), start = 1_000, end = 4_000)
        val local = PendingRepository(listOf(pending, other))
        val remote = PendingRemote(listOf(
            submission(1, pending.problem.id, "OK", 500), // before start
            submission(2, pending.problem.id, "OK", 1_500), // during
            submission(3, pending.problem.id, "WRONG_ANSWER", 2_500),
            submission(4, ProblemId(999, "A"), "OK", 2_500),
            submission(5, pending.problem.id, "OK", 3_500),
            submission(6, pending.problem.id, "OK", 3_000),
            submission(7, other.problem.id, "OK", 3_500), // during other Ipsum
        ))

        assertEquals(UpdatePendingIpsumsResult.Success(1), UpdatePendingIpsums(local, remote)())
        assertEquals(3_000L, local.values.value.single { it.id == 1L }.solvedOutsideAtEpochMillis)
        assertEquals(IpsumStatus.PENDING, local.values.value.single { it.id == 2L }.status)
        assertEquals(2_000L, remote.since)

        assertEquals(UpdatePendingIpsumsResult.Success(0), UpdatePendingIpsums(local, remote)())
        assertEquals(3_000L, local.values.value.single { it.id == 1L }.solvedOutsideAtEpochMillis)
    }

    @Test
    fun `without pending Ipsums succeeds without remote request`() = runTest {
        val local = PendingRepository(emptyList())
        val remote = PendingRemote(emptyList())

        assertEquals(UpdatePendingIpsumsResult.Success(0), UpdatePendingIpsums(local, remote)())
        assertEquals(0, remote.calls)
    }

    @Test
    fun `maps every recoverable remote response`() = runTest {
        val expected = listOf(
            SubmissionHistoryResult.Failed("bad") to UpdatePendingIpsumsResult.Failed("bad"),
            SubmissionHistoryResult.RateLimited to UpdatePendingIpsumsResult.RateLimited,
            SubmissionHistoryResult.NetworkFailure to UpdatePendingIpsumsResult.NetworkFailure,
            SubmissionHistoryResult.HttpFailure(503) to UpdatePendingIpsumsResult.HttpFailure(503),
            SubmissionHistoryResult.InvalidResponse to UpdatePendingIpsumsResult.InvalidResponse,
        )
        expected.forEach { (remoteResult, domainResult) ->
            val local = PendingRepository(listOf(ipsum(1, ProblemId(1, "A"), 1, 2)))
            assertEquals(domainResult, UpdatePendingIpsums(local, PendingRemote(result = remoteResult))())
        }
    }

    private fun ipsum(id: Long, problemId: ProblemId, start: Long, end: Long) = Ipsum(
        id, "tourist", CodeforcesProblem(problemId, "Problem", 1200, setOf("dp")), 1200,
        IpsumCategory.CURRENT_LEVEL, 1200, 1200, 1200, 0, start,
        endedAtEpochMillis = end, errorCount = 2, failureReason = IpsumFailureReason.LOGIC_NOT_FOUND,
        ratingDelta = -10, finalLoremRating = 1190, expectedTimeMillis = 60_000,
        status = IpsumStatus.PENDING,
    )

    private fun submission(id: Long, problemId: ProblemId, verdict: String, time: Long) =
        CodeforcesSubmission(id, problemId, verdict, Instant.ofEpochMilli(time))

    private class PendingRemote(
        private val submissions: List<CodeforcesSubmission> = emptyList(),
        private val result: SubmissionHistoryResult? = null,
    ) : CodeforcesRepository {
        var calls = 0
        var since: Long? = null
        override suspend fun user(handle: String) = error("unused")
        override suspend fun submissionHistory(handle: String) = error("unbounded call")
        override suspend fun submissionHistorySince(handle: String, sinceEpochMillis: Long): SubmissionHistoryResult {
            calls++
            since = sinceEpochMillis
            return result ?: SubmissionHistoryResult.Success(submissions)
        }
        override suspend fun problems() = error("unused")
    }

    private class PendingRepository(initial: List<Ipsum>) : LoremRepository {
        override val profile = MutableStateFlow<LocalProfile?>(LocalProfile("tourist", "Tourist", 1200, 1200, null, 0))
        val values = MutableStateFlow(initial)
        override val ipsums: Flow<List<Ipsum>> = values
        override val activeIpsum = MutableStateFlow<Ipsum?>(null)
        override val problemHistory = MutableStateFlow(emptyList<ProblemHistory>())
        override val problemCatalog = MutableStateFlow(emptyList<CodeforcesProblem>())
        override val catalogLastUpdatedEpochMillis = MutableStateFlow<Long?>(null)
        override suspend fun saveProfile(profile: LocalProfile) = Unit
        override suspend fun clearProfile() = Unit
        override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) = Unit
        override suspend fun replaceProblemCatalog(problems: List<CodeforcesProblem>, updatedAtEpochMillis: Long) = Unit
        override suspend fun createActiveIpsum(ipsum: Ipsum) = ipsum
        override suspend fun revealIpsumHint(ipsumId: Long, revealedAtEpochMillis: Long) = Unit
        override suspend fun markIpsumsSolvedOutside(firstAcceptedAtByIpsumId: Map<Long, Long>): Int {
            var changed = 0
            values.value = values.value.map { ipsum ->
                val time = firstAcceptedAtByIpsumId[ipsum.id]
                if (time != null && ipsum.status == IpsumStatus.PENDING && ipsum.solvedOutsideAtEpochMillis == null) {
                    changed++
                    ipsum.copy(status = IpsumStatus.SOLVED_OUTSIDE, solvedOutsideAtEpochMillis = time)
                } else ipsum
            }
            return changed
        }
    }
}
