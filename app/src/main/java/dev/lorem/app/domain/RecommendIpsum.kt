package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.ProblemHistory
import kotlin.math.abs
import kotlin.math.max

fun interface RandomSource {
    fun nextInt(bound: Int): Int
}

data class IpsumRecommendation(
    val problem: CodeforcesProblem,
    val category: IpsumCategory,
    val normalizedLoremRating: Int,
    val desiredRatingMin: Int,
    val desiredRatingMax: Int,
    val fallbackDistance: Int,
)

class RecommendIpsum(private val random: RandomSource) {
    fun recommend(
        loremRating: Int,
        catalog: List<CodeforcesProblem>,
        history: List<ProblemHistory>,
        ipsums: List<Ipsum>,
    ): IpsumRecommendation? {
        val category = IpsumCategory.entries[random.nextInt(IpsumCategory.entries.size)]
        val normalizedRating = max(MIN_RATING, ((loremRating + 50) / 100) * 100)
        val desiredRatings = when (category) {
            IpsumCategory.FLUENCY -> setOf(max(MIN_RATING, normalizedRating - 200), max(MIN_RATING, normalizedRating - 100))
            IpsumCategory.CURRENT_LEVEL -> setOf(normalizedRating)
            IpsumCategory.CHALLENGE -> setOf(normalizedRating + 100, normalizedRating + 200)
        }
        val excluded = history.filter { it.attempted || it.hasAcceptedSubmission }.mapTo(mutableSetOf()) { it.problemId }
        excluded += ipsums.map { it.problem.id }
        val eligible = catalog.filter { it.rating != null && it.id !in excluded }
        val practiceCount = mutableMapOf<String, Int>()
        val practicedIds = history.filter(ProblemHistory::attempted).mapTo(mutableSetOf()) { it.problemId }.apply {
            addAll(ipsums.map { it.problem.id })
        }
        catalog.filter { it.id in practicedIds }.flatMap { it.tags }.forEach { tag ->
            practiceCount[tag] = practiceCount.getOrDefault(tag, 0) + 1
        }

        for (distance in 0..MAX_FALLBACK step 100) {
            val layer = eligible.filter { problem ->
                desiredRatings.minOf { abs(requireNotNull(problem.rating) - it) } == distance
            }
            if (layer.isNotEmpty()) {
                val bestUnseen = layer.maxOf { problem -> problem.tags.count { practiceCount.getOrDefault(it, 0) == 0 } }
                val unseenBest = layer.filter { it.tags.count { tag -> practiceCount.getOrDefault(tag, 0) == 0 } == bestUnseen }
                val lowestPractice = unseenBest.minOf { problem -> problem.tags.sumOf { practiceCount.getOrDefault(it, 0) } }
                val best = unseenBest.filter { problem -> problem.tags.sumOf { practiceCount.getOrDefault(it, 0) } == lowestPractice }
                return IpsumRecommendation(
                    problem = best[random.nextInt(best.size)],
                    category = category,
                    normalizedLoremRating = normalizedRating,
                    desiredRatingMin = desiredRatings.min(),
                    desiredRatingMax = desiredRatings.max(),
                    fallbackDistance = distance,
                )
            }
        }
        return null
    }

    private companion object {
        const val MIN_RATING = 800
        const val MAX_FALLBACK = 300
    }
}
