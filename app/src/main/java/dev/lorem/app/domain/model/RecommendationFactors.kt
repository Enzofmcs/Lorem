package dev.lorem.app.domain.model

data class TagNeed(
    val tag: String,
    val exposure: Int,
    val attempts: Int,
    val accepted: Int,
    val hints: Int,
) {
    val failureRate: Double? get() = if (attempts == 0) null else (attempts - accepted).toDouble() / attempts
    val hintRate: Double? get() = if (attempts == 0) null else hints.toDouble() / attempts
}

/** A snapshot of the selection inputs, kept private until the attempt ends. */
data class RecommendationFactors(val tags: List<TagNeed>, val recentOverlap: Int) {
    val newTagCount: Int get() = tags.count { it.exposure == 0 }
    val totalExposure: Long get() = tags.sumOf { it.exposure.toLong() }
    val averageFailure: Double get() = tags.mapNotNull { it.failureRate }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val averageHintUse: Double get() = tags.mapNotNull { it.hintRate }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
}
