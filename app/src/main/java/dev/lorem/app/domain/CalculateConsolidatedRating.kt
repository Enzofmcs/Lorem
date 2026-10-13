package dev.lorem.app.domain

import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumStatus
import kotlin.math.max

object ConsolidationRules {
    const val WINDOW = 20
    const val MIN_ATTEMPTS = 8
    const val MIN_AC_PERCENT = 70
    const val MIN_WITHOUT_HINT = 5
    const val MIN_TAGS = 3
    const val MIN_STYLES = 3
}

data class BandProgress(
    val rating: Int,
    val attempts: Int,
    val accepted: Int,
    val acceptedWithoutHint: Int,
    val tagCount: Int,
    val styleCount: Int,
) {
    val qualified: Boolean get() = attempts >= ConsolidationRules.MIN_ATTEMPTS &&
        accepted * 100 >= attempts * ConsolidationRules.MIN_AC_PERCENT &&
        acceptedWithoutHint >= ConsolidationRules.MIN_WITHOUT_HINT &&
        tagCount >= ConsolidationRules.MIN_TAGS && styleCount >= ConsolidationRules.MIN_STYLES
}

data class ConsolidationProgress(val consolidatedRating: Int?, val nextBand: BandProgress)

/** Consolidation is a permanent milestone. Replaying windows recovers missed writes after restart. */
fun calculateConsolidatedRating(
    ipsums: List<Ipsum>,
    previousRating: Int? = null,
    ownerHandle: String? = null,
    estimatedRating: Int = 800,
): ConsolidationProgress {
    val closed = ipsums.asSequence()
        .filter { ownerHandle == null || it.ownerHandle.trim().equals(ownerHandle.trim(), ignoreCase = true) }
        .distinctBy { it.id }
        .filter { it.status != IpsumStatus.ACTIVE && it.endedAtEpochMillis != null }
        .sortedWith(compareBy<Ipsum> { it.endedAtEpochMillis }.thenBy { it.id })
        .toList()
    var highest = previousRating
    closed.indices.forEach { end ->
        val window = closed.subList(max(0, end + 1 - ConsolidationRules.WINDOW), end + 1)
        window.groupBy { it.selectedRating / 100 * 100 }.forEach { (band, values) ->
            if (band >= 800 && bandProgress(band, values).qualified) highest = max(highest ?: 800, band)
        }
    }
    val next = highest?.plus(100) ?: max(800, estimatedRating / 100 * 100)
    val recent = closed.takeLast(ConsolidationRules.WINDOW).filter { it.selectedRating in next..next + 99 }
    return ConsolidationProgress(highest, bandProgress(next, recent))
}

private fun bandProgress(rating: Int, values: List<Ipsum>): BandProgress {
    val accepted = values.filter { it.status == IpsumStatus.COMPLETED }
    val autonomous = accepted.filter { it.hintRevealedAtEpochMillis == null }
    return BandProgress(
        rating, values.size, accepted.size, autonomous.size,
        autonomous.flatMap { it.problem.tags }.toSet().size,
        autonomous.map { it.problem.tags }.filter { it.isNotEmpty() }.distinct().size,
    )
}
