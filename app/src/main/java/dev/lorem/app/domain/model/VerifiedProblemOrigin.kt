package dev.lorem.app.domain.model

/** Evidence from contest.list; never infer a date from the contest id or problem name. */
data class VerifiedProblemOrigin(
    val contestId: Long,
    val startedAtEpochMillis: Long,
    val verifiedAtEpochMillis: Long,
    val source: String = SOURCE,
    val eligibilityVersion: String = VERSION,
) {
    companion object {
        const val SOURCE = "codeforces:contest.list"
        const val VERSION = "origin-20200701-v1"
        const val CUTOFF_EPOCH_MILLIS = 1_593_561_600_000L
    }
}

fun CodeforcesProblem.hasVerifiedOrigin(): Boolean = origin?.let {
    it.contestId == id.contestId && it.source == VerifiedProblemOrigin.SOURCE &&
        it.eligibilityVersion == VerifiedProblemOrigin.VERSION &&
        it.startedAtEpochMillis > 0 && it.verifiedAtEpochMillis > 0
} == true

fun CodeforcesProblem.isEligibleForNewRecommendation(): Boolean = rating != null && hasVerifiedOrigin() &&
    requireNotNull(origin).startedAtEpochMillis >= VerifiedProblemOrigin.CUTOFF_EPOCH_MILLIS
