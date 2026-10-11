package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.LocalProfile

/** A missing denominator is different from an observed rate of zero. */
data class AcRate(val accepted: Int, val attempts: Int) {
    val percent: Double? get() = if (attempts == 0) null else accepted * 100.0 / attempts
}

data class AttemptStatistics(
    val total: Int,
    val accepted: Int,
    val pending: Int,
    val solvedOutside: Int,
    val hintCount: Int,
    val withoutHint: AcRate,
    val withHint: AcRate,
    val timedAttempts: Int,
    val averageTimeMillis: Double?,
    val errorsBeforeAc: Long,
) {
    val acRate: AcRate get() = AcRate(accepted, total)
    val failures: Int get() = total - accepted
    val failurePercent: Double? get() = if (total == 0) null else failures * 100.0 / total
    val averageErrorsBeforeAc: Double? get() = if (accepted == 0) null else errorsBeforeAc.toDouble() / accepted
}

data class RatingStatistics(val problemRating: Int, val attempts: AttemptStatistics)
data class TagStatistics(val tag: String, val attempts: AttemptStatistics)

data class RatingEvolutionPoint(
    val ipsumId: Long,
    val endedAtEpochMillis: Long,
    val before: Int,
    val delta: Int,
    val after: Int,
)

data class IpsumStatistics(
    val ownerHandle: String,
    val currentRating: Int,
    val overall: AttemptStatistics,
    val byRating: List<RatingStatistics>,
    val byTag: List<TagStatistics>,
    val ratingEvolution: List<RatingEvolutionPoint>,
    val unratedAttempts: Int,
) {
    val mostPracticed: List<TagStatistics> get() = byTag
        .filter { it.attempts.total > 0 }
        .sortedWith(compareByDescending<TagStatistics> { it.attempts.total }.thenBy { it.tag })
    val leastPracticed: List<TagStatistics> get() = byTag
        .sortedWith(compareBy<TagStatistics> { it.attempts.total }.thenBy { it.tag })
    val highestFailure: List<TagStatistics> get() = byTag
        .filter { it.attempts.failures > 0 }
        .sortedWith(compareByDescending<TagStatistics> { it.attempts.failurePercent }
            .thenByDescending { it.attempts.total }.thenBy { it.tag })
}

/**
 * Statistics describe the original, finished attempt, never the upsolve as a new AC.
 * The catalog only supplies official tag names (including zero exposure). Active
 * attempts do not supply tags, ratings or any other input to these aggregates.
 */
fun buildIpsumStatistics(
    profile: LocalProfile,
    ipsums: List<Ipsum>,
    catalog: List<CodeforcesProblem> = emptyList(),
): IpsumStatistics {
    val finished = ipsums.asSequence()
        .filter { it.ownerHandle.trim().equals(profile.handle.trim(), ignoreCase = true) }
        .filter { it.status != IpsumStatus.ACTIVE }
        .distinctBy { it.id }
        .sortedWith(compareBy<Ipsum> { it.endedAtEpochMillis ?: it.startedAtEpochMillis }.thenBy { it.id })
        .toList()
    val tags = (catalog.flatMap { it.tags } + finished.flatMap { it.problem.tags }).distinct().sorted()
    val byTag = finished.flatMap { ipsum -> ipsum.problem.tags.map { it to ipsum } }
        .groupBy({ it.first }, { it.second })
    val evolution = finished.mapNotNull { ipsum ->
        val endedAt = ipsum.endedAtEpochMillis ?: return@mapNotNull null
        val delta = ipsum.ratingDelta ?: return@mapNotNull null
        val after = ipsum.finalLoremRating ?: return@mapNotNull null
        RatingEvolutionPoint(ipsum.id, endedAt, ipsum.initialLoremRating, delta, after)
    }
    return IpsumStatistics(
        ownerHandle = profile.handle,
        currentRating = profile.loremRating,
        overall = summarizeAttempts(finished),
        byRating = finished.groupBy { it.selectedRating }.toSortedMap()
            .map { (rating, attempts) -> RatingStatistics(rating, summarizeAttempts(attempts)) },
        byTag = tags.map { TagStatistics(it, summarizeAttempts(byTag[it].orEmpty())) },
        ratingEvolution = evolution,
        unratedAttempts = finished.size - evolution.size,
    )
}

private fun summarizeAttempts(ipsums: List<Ipsum>): AttemptStatistics {
    val accepted = ipsums.filter { it.status == IpsumStatus.COMPLETED }
    val withHint = ipsums.filter { it.hintRevealedAtEpochMillis != null }
    val withoutHint = ipsums.filter { it.hintRevealedAtEpochMillis == null }
    val times = ipsums.mapNotNull { ipsum ->
        ipsum.endedAtEpochMillis?.takeIf { it >= ipsum.startedAtEpochMillis }
            ?.let { it - ipsum.startedAtEpochMillis }
    }
    return AttemptStatistics(
        total = ipsums.size,
        accepted = accepted.size,
        pending = ipsums.count { it.status == IpsumStatus.PENDING },
        solvedOutside = ipsums.count {
            it.status == IpsumStatus.SOLVED_OUTSIDE && it.solvedOutsideAtEpochMillis != null
        },
        hintCount = withHint.size,
        withoutHint = AcRate(withoutHint.count { it.status == IpsumStatus.COMPLETED }, withoutHint.size),
        withHint = AcRate(withHint.count { it.status == IpsumStatus.COMPLETED }, withHint.size),
        timedAttempts = times.size,
        averageTimeMillis = times.takeIf { it.isNotEmpty() }?.map { it.toDouble() }?.average(),
        errorsBeforeAc = accepted.sumOf { it.errorCount.toLong() },
    )
}
