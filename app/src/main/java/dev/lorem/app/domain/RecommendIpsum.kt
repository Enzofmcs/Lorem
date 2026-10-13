package dev.lorem.app.domain

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.RecommendationFactors
import dev.lorem.app.domain.model.TagNeed
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
    val factors: RecommendationFactors,
)

class RecommendIpsum(private val random: RandomSource) {
    fun recommend(
        loremRating: Int,
        catalog: List<CodeforcesProblem>,
        history: List<ProblemHistory>,
        ipsums: List<Ipsum>,
        ownerHandle: String = ipsums.firstOrNull()?.ownerHandle.orEmpty(),
    ): IpsumRecommendation? {
        val owned = ipsums.filter { ownerHandle.isBlank() || it.ownerHandle.trim().equals(ownerHandle.trim(), true) }
        val category = IpsumCategory.entries[random.nextInt(IpsumCategory.entries.size)]
        val normalizedRating = max(MIN_RATING, ((loremRating + 50) / 100) * 100)
        val desiredRatings = when (category) {
            IpsumCategory.FLUENCY -> setOf(max(MIN_RATING, normalizedRating - 200), max(MIN_RATING, normalizedRating - 100))
            IpsumCategory.CURRENT_LEVEL -> setOf(normalizedRating)
            IpsumCategory.CHALLENGE -> setOf(normalizedRating + 100, normalizedRating + 200)
        }
        val excluded = history.filter { it.attempted || it.hasAcceptedSubmission }.mapTo(mutableSetOf()) { it.problemId }
        excluded += owned.map { it.problem.id }
        val eligible = catalog.distinctBy { it.id }.filter { it.rating != null && it.id !in excluded }
        val practiceCount = mutableMapOf<String, Int>()
        val practicedIds = history.filter { it.attempted || it.hasAcceptedSubmission }.mapTo(mutableSetOf()) { it.problemId }.apply {
            addAll(owned.map { it.problem.id })
        }
        val knownProblems = catalog.associateBy { it.id }.toMutableMap().apply {
            owned.forEach { put(it.problem.id, it.problem) }
        }
        knownProblems.values.filter { it.id in practicedIds }.flatMap { it.tags }.forEach { tag ->
            practiceCount[tag] = practiceCount.getOrDefault(tag, 0) + 1
        }
        val profile = LocalProfile(ownerHandle, ownerHandle, null, loremRating, null, 0)
        val performance = buildIpsumStatistics(profile, owned, catalog).byTag.associateBy { it.tag }
        val recentTags = owned.filter { it.status != IpsumStatus.ACTIVE && it.endedAtEpochMillis != null }
            .maxWithOrNull(compareBy<Ipsum> { it.endedAtEpochMillis }.thenBy { it.id })?.problem?.tags.orEmpty()
        fun factors(problem: CodeforcesProblem) = RecommendationFactors(
            problem.tags.sorted().map { tag ->
                val attempts = performance[tag]?.attempts
                TagNeed(tag, practiceCount[tag] ?: 0, attempts?.total ?: 0, attempts?.accepted ?: 0, attempts?.hintCount ?: 0)
            },
            problem.tags.count { it in recentTags },
        )
        val priority = compareBy<RecommendationFactors> { if (it.tags.isEmpty()) 0 else 1 }
            .thenBy { it.newTagCount }.thenBy { -it.totalExposure }
            .thenBy { it.averageFailure }.thenBy { it.averageHintUse }.thenBy { -it.recentOverlap }

        for (distance in 0..MAX_FALLBACK step 100) {
            val layer = eligible.filter { problem ->
                desiredRatings.minOf { abs(requireNotNull(problem.rating) - it) } == distance
            }
            if (layer.isNotEmpty()) {
                val scored = layer.map { it to factors(it) }
                val bestScore = scored.maxWithOrNull { a, b -> priority.compare(a.second, b.second) }!!.second
                val best = scored.filter { priority.compare(it.second, bestScore) == 0 }
                val selected = best[random.nextInt(best.size)]
                return IpsumRecommendation(
                    problem = selected.first,
                    category = category,
                    normalizedLoremRating = normalizedRating,
                    desiredRatingMin = desiredRatings.min(),
                    desiredRatingMax = desiredRatings.max(),
                    fallbackDistance = distance,
                    factors = selected.second,
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
