package dev.lorem.app.domain.cloud

import dev.lorem.app.domain.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.domain.sync.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CloudTrainingTest {
    private val start = 1_800_000_000_000L
    private fun attempt(cutoff: Long? = start + IPSUM_LIMIT_MILLIS) = CloudIpsum("command", "tourist", 0,
        if (cutoff == null) CloudIpsumState.ACTIVE else CloudIpsumState.CLOSED_AWAITING_VERIFICATION,
        Ipsum(1, "tourist", CodeforcesProblem(ProblemId(2000, "A"), "Problem", 800, setOf("math")),
            800, IpsumCategory.CURRENT_LEVEL, 800, 800, 800, 0, start, status = IpsumStatus.ACTIVE),
        cutoff, if (cutoff == start + IPSUM_LIMIT_MILLIS) CloudCloseReason.TIME_LIMIT_EXCEEDED else CloudCloseReason.GIVE_UP)
    private fun submission(id: Long, time: Long, verdict: String?) = CodeforcesSubmission(id, ProblemId(2000, "A"), verdict, Instant.ofEpochMilli(time))

    @Test fun `experimental attempt cannot finalize without its frozen model and original attempts need no new model`() {
        val a = attempt()
        val trial = a.copy(ipsum = a.ipsum.copy(virtualDuelSnapshot = VirtualDuelSnapshot(800*DUEL_SCALE,800,0)))
        assertThrows(IllegalArgumentException::class.java) { finalizeCloudIpsum(trial, CloudVerification.NoAccepted(0),null) }
        val curve = DuelCurve(listOf(1000),listOf(500000))
        val result = finalizeCloudIpsum(trial,CloudVerification.NoAccepted(0),null) { snapshot, elapsed, ac ->
            calculateVirtualDuel(snapshot,curve,elapsed,ac)
        }
        assertEquals(-8000000L,result.virtualDuelResult!!.deltaMicros)
        assertNull(finalizeCloudIpsum(a,CloudVerification.NoAccepted(0),null).virtualDuelResult)
        val hinted=trial.copy(ipsum=trial.ipsum.copy(hintRevealedAtEpochMillis=start+1000))
        assertThrows(IllegalStateException::class.java) { finalizeCloudIpsum(hinted,CloudVerification.NoAccepted(0),null) { snapshot,elapsed,ac -> calculateVirtualDuel(snapshot,curve,elapsed,ac) } }
    }
    @Test fun `canonical handles and caches separate uids versions and case`() {
        assertEquals("cf_tourist", cloudHandleKey("  ToUrIsT "))
        for (bad in listOf("../x", "x/y", "bad handle", "", "a".repeat(65))) {
            try { cloudHandleKey(bad); fail(bad) } catch (_: dev.lorem.app.domain.handles.HandleException) { }
        }
        assertNotEquals(syncKey(SyncDataset.HISTORY, ProfileLink.forAccount("A", "tourist", 1, 1)),
            syncKey(SyncDataset.HISTORY, ProfileLink.forAccount("B", "tourist", 1, 1)))
        assertNotEquals(syncKey(SyncDataset.HISTORY, ProfileLink.forAccount("A", "tourist", 1, 1)),
            syncKey(SyncDataset.HISTORY, ProfileLink.forAccount("A", "tourist", 1, 2)))
    }
    @Test fun `deadline before exactly and after two hours uses monotonic time`() {
        var elapsed = 100L
        var boot = 1
        val clock = CloudDeadline({ elapsed }, { boot })
        clock.confirm("A", start, elapsed)
        elapsed += IPSUM_LIMIT_MILLIS - 1
        assertEquals(start + IPSUM_LIMIT_MILLIS - 1, clock.now("A"))
        elapsed++
        assertEquals(start + IPSUM_LIMIT_MILLIS, clock.now("A"))
        elapsed++
        assertEquals(start + IPSUM_LIMIT_MILLIS + 1, clock.now("A"))
        assertNull(clock.now("B"))
        boot++
        assertNull(clock.now("A"))
        clock.confirm("A", start, elapsed)
        elapsed = 0
        assertNull(clock.now("A"))
    }
    @Test fun `inclusive AC boundary ignores before start after deadline and other problems`() {
        val a = attempt()
        val invalid = listOf(submission(1, start - 1000, "OK"), submission(2, a.expiresAtMillis + 1000, "OK"),
            submission(3, start, "OK").copy(problemId = ProblemId(2000, "B")))
        assertEquals(CloudVerification.NoAccepted(0), verifyCloudWindow(a, invalid))
        assertEquals(CloudVerification.Accepted(a.expiresAtMillis, 0), verifyCloudWindow(a,
            invalid + submission(4, a.expiresAtMillis, "OK")))
        assertEquals(CloudVerification.Accepted(start, 0), verifyCloudWindow(a, listOf(submission(5, start, "OK"))))
    }
    @Test fun `pending verdicts delay evaluation even with AC to preserve first AC timing and rating`() {
        val a = attempt()
        for (verdict in listOf(null, "", "TESTING")) {
            assertEquals(CloudVerification.Awaiting, verifyCloudWindow(a, listOf(submission(1, start + 1000, verdict))))
            assertEquals(CloudVerification.Awaiting, verifyCloudWindow(a,
                listOf(submission(1, start + 1000, verdict), submission(2, start + 2000, "OK"))))
        }
        assertEquals(CloudVerification.Accepted(start + 1000, 0), verifyCloudWindow(a,
            listOf(submission(1, start + 1000, "OK"), submission(2, start + 2000, "OK"))))
    }
    @Test fun `dedup WA then AC snapshots D012 and no 800 floor`() {
        val a = attempt()
        val wa = submission(1, start + 1000, "WRONG_ANSWER")
        val v = verifyCloudWindow(a, listOf(wa, wa, submission(2, start + 2000, "OK"), submission(3, start + 3000, "WRONG_ANSWER")))
        val result = finalizeCloudIpsum(a, v, null)
        assertEquals(1, result.errorCount)
        assertEquals(IpsumStatus.COMPLETED, result.status)
        assertEquals(calculateLoremRating(800, 800, true, 2000, false).ratingAfter, result.finalLoremRating)
        val timeout = finalizeCloudIpsum(a, CloudVerification.NoAccepted(0), null)
        assertEquals(IPSUM_LIMIT_MILLIS, timeout.totalTimeMillis())
        assertEquals(IpsumFailureReason.TIME_EXPIRED, timeout.failureReason)
        assertTrue(timeout.finalLoremRating!! < 800)
    }
    @Test fun `manual boundary is earlier than timeout and late acceptance cannot change evaluation`() {
        val a = attempt(start + 60_000)
        val v = verifyCloudWindow(a, listOf(submission(1, start + 61_000, "OK")))
        val result = finalizeCloudIpsum(a, v, IpsumFailureReason.LOGIC_NOT_FOUND)
        assertEquals(IpsumStatus.PENDING, result.status)
        assertEquals(60_000L, result.totalTimeMillis())
        assertEquals(IpsumFailureReason.LOGIC_NOT_FOUND, result.failureReason)
    }
    @Test fun `rejudge duplicate uses definitive AC and ids order errors within same CF second`() {
        val v = verifyCloudWindow(attempt(), listOf(submission(1, start + 1000, "WRONG_ANSWER"),
            submission(2, start + 1000, "TESTING"), submission(2, start + 1000, "OK"),
            submission(3, start + 1000, "WRONG_ANSWER")))
        assertEquals(CloudVerification.Accepted(start + 1000, 1), v)
    }
    @Test fun `submillisecond server start does not accept a submission from earlier in that second`() {
        val a = attempt().copy(firstEligibleSubmissionMillis = start + 1000)
        assertEquals(CloudVerification.NoAccepted(0), verifyCloudWindow(a, listOf(submission(1, start, "OK"))))
        assertEquals(CloudVerification.Accepted(start + 1000, 0), verifyCloudWindow(a, listOf(submission(2, start + 1000, "OK"))))
    }
}
