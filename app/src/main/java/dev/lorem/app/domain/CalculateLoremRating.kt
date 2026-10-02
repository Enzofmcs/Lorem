package dev.lorem.app.domain

import kotlin.math.pow
import kotlin.math.roundToInt

data class LoremRatingCalculation(
    val ratingBefore: Int,
    val problemRating: Int,
    val accepted: Boolean,
    val usedHint: Boolean,
    val elapsedMillis: Long,
    val expectedTimeMillis: Long,
    val delta: Int,
    val ratingAfter: Int,
)

/** Pure Rating Lorem calculation defined by D-012. */
fun calculateLoremRating(
    ratingBefore: Int,
    problemRating: Int,
    accepted: Boolean,
    elapsedMillis: Long,
    usedHint: Boolean,
): LoremRatingCalculation {
    require(ratingBefore >= 0)
    require(problemRating >= 0)
    require(elapsedMillis >= 0)

    val expectedMinutes = (60.0 + (problemRating - ratingBefore) / 10.0).coerceIn(30.0, 120.0)
    val expectedMillis = (expectedMinutes * 60_000).roundToInt().toLong()
    val expectation = 1.0 / (1.0 + 10.0.pow((problemRating - ratingBefore) / 400.0))
    val delta = if (accepted) {
        val baseGain = (8.0 + 32.0 * (1.0 - expectation)).roundToInt()
        val timeFactor = when {
            elapsedMillis <= expectedMillis -> 1.0
            elapsedMillis >= expectedMillis * 2 -> 0.5
            else -> 1.0 - 0.5 * (elapsedMillis - expectedMillis).toDouble() / expectedMillis
        }
        val timedGain = (baseGain * timeFactor).roundToInt().coerceAtLeast(1)
        val hintPenalty = if (usedHint) maxOf(2, (timedGain * 0.25).roundToInt()) else 0
        (timedGain - hintPenalty).coerceIn(1, 40)
    } else {
        -(8.0 + 24.0 * expectation).roundToInt().coerceIn(1, 32)
    }
    return LoremRatingCalculation(
        ratingBefore = ratingBefore,
        problemRating = problemRating,
        accepted = accepted,
        usedHint = usedHint,
        elapsedMillis = elapsedMillis,
        expectedTimeMillis = expectedMillis,
        delta = delta,
        ratingAfter = (ratingBefore + delta).coerceAtLeast(0),
    )
}
