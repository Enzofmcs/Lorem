package dev.lorem.app.domain.cloud

import dev.lorem.app.domain.calculateLoremRating
import dev.lorem.app.domain.model.*
import java.util.Locale

const val IPSUM_LIMIT_MILLIS = 7_200_000L
const val CLOUD_RULE_VERSION = "spark-2h-v1"

/** Codeforces handles use this alphabet; the lowercase value is already a safe Firestore key. */
fun cloudHandleKey(handle: String): String = handle.trim().lowercase(Locale.ROOT).also {
    require(it.matches(Regex("[a-z0-9_.-]{3,24}"))) { "Handle Codeforces inválido." }
}

data class CloudHandle(
    val key: String,
    val handle: String,
    val rating: Int,
    val consolidatedRating: Int?,
    val version: Long,
)

enum class CloudIpsumState { ACTIVE, CLOSED_AWAITING_VERIFICATION, FINAL }
enum class CloudCloseReason { GIVE_UP, TIME_LIMIT_EXCEEDED }

data class CloudIpsum(
    val id: String,
    val handleKey: String,
    val version: Long,
    val state: CloudIpsumState,
    val ipsum: Ipsum,
    val cutoffMillis: Long? = null,
    val closeReason: CloudCloseReason? = null,
    val processedAtMillis: Long? = null,
    val firstEligibleSubmissionMillis: Long = ((ipsum.startedAtEpochMillis + 999) / 1000) * 1000,
) {
    val expiresAtMillis: Long get() = ipsum.startedAtEpochMillis + IPSUM_LIMIT_MILLIS
    val boundaryMillis: Long get() = cutoffMillis ?: expiresAtMillis
}

data class CloudSnapshot(
    val handles: List<CloudHandle>,
    val active: CloudIpsum?,
    val attempts: List<CloudIpsum>,
    val online: Boolean,
)

/** Anchor only a confirmed server time to elapsedRealtime; wall clock changes never add time. */
class CloudDeadline(private val elapsedMillis: () -> Long, private val bootId: () -> Int) {
    private data class Anchor(val server: Long, val elapsed: Long, val boot: Int)
    private val anchors = java.util.concurrent.ConcurrentHashMap<String, Anchor>()
    @Synchronized fun confirm(uid: String, serverMillis: Long, elapsedAtRequest: Long) {
        val previousNow = now(uid)
        val conservativeNow = serverMillis + (elapsedMillis() - elapsedAtRequest).coerceAtLeast(0)
        if (previousNow == null || conservativeNow >= previousNow) {
            anchors[uid] = Anchor(serverMillis, elapsedAtRequest, bootId())
        }
    }
    fun now(uid: String): Long? = anchors[uid]?.let {
        val elapsed = elapsedMillis() - it.elapsed
        if (it.boot != bootId() || elapsed < 0) null else it.server + elapsed
    }
    fun clear(uid: String) { anchors.remove(uid) }
}

sealed interface CloudVerification {
    data class Accepted(val atMillis: Long, val errors: Int) : CloudVerification
    data class NoAccepted(val errors: Int) : CloudVerification
    data object Awaiting : CloudVerification
}

/** A Success from the centralized API means all pages covering this window were received. */
fun verifyCloudWindow(attempt: CloudIpsum, submissions: List<CodeforcesSubmission>): CloudVerification {
    val window = submissions.groupBy { it.id }.values.map { versions ->
        versions.firstOrNull { it.verdict == "OK" }
            ?: versions.firstOrNull { !it.verdict.isNullOrBlank() && it.verdict != "TESTING" } ?: versions.first()
    }.filter {
        it.problemId == attempt.ipsum.problem.id &&
            it.createdAt.toEpochMilli() >= attempt.firstEligibleSubmissionMillis &&
            it.createdAt.toEpochMilli() <= attempt.boundaryMillis
    }.sortedWith(compareBy({ it.createdAt }, { it.id }))
    val accepted = window.firstOrNull { it.verdict == "OK" }
    val beforeAccepted = if (accepted == null) window else window.takeWhile { it.id != accepted.id }
    val errors = beforeAccepted.count { !it.verdict.isNullOrBlank() && it.verdict !in setOf("OK", "TESTING") }
    if (window.any { it.verdict.isNullOrBlank() || it.verdict == "TESTING" }) return CloudVerification.Awaiting
    if (accepted != null) return CloudVerification.Accepted(accepted.createdAt.toEpochMilli(), errors)
    return CloudVerification.NoAccepted(errors)
}

fun finalizeCloudIpsum(attempt: CloudIpsum, verification: CloudVerification, failure: IpsumFailureReason?): Ipsum {
    require(verification != CloudVerification.Awaiting)
    val accepted = verification is CloudVerification.Accepted
    val endedAt = (verification as? CloudVerification.Accepted)?.atMillis ?: requireNotNull(attempt.cutoffMillis)
    val calculation = calculateLoremRating(attempt.ipsum.initialLoremRating, attempt.ipsum.selectedRating,
        accepted, endedAt - attempt.ipsum.startedAtEpochMillis, attempt.ipsum.hintRevealedAtEpochMillis != null)
    return attempt.ipsum.copy(
        endedAtEpochMillis = endedAt,
        status = if (accepted) IpsumStatus.COMPLETED else IpsumStatus.PENDING,
        failureReason = if (accepted) null else if (attempt.closeReason == CloudCloseReason.TIME_LIMIT_EXCEEDED)
            IpsumFailureReason.TIME_EXPIRED else requireNotNull(failure),
        errorCount = when (verification) {
            is CloudVerification.Accepted -> verification.errors
            is CloudVerification.NoAccepted -> verification.errors
            else -> error("Awaiting verification")
        },
        ratingDelta = calculation.delta, finalLoremRating = calculation.ratingAfter,
        expectedTimeMillis = calculation.expectedTimeMillis,
    )
}

interface CloudTrainingRepository {
    val deadline: CloudDeadline
    suspend fun load(uid: String, selectedKey: String? = null): CloudSnapshot
    suspend fun addHandle(uid: String, handle: String): CloudHandle
    suspend fun selectedHandle(uid: String): String?
    suspend fun selectHandle(uid: String, key: String)
    suspend fun start(uid: String, key: String, commandId: String): CloudIpsum
    suspend fun hint(uid: String, attempt: CloudIpsum)
    suspend fun close(uid: String, attempt: CloudIpsum, reason: IpsumFailureReason)
    suspend fun reconcile(uid: String, attempt: CloudIpsum): Boolean
}
