package dev.lorem.app.domain.cloud

import dev.lorem.app.domain.IpsumRecommendation
import dev.lorem.app.domain.model.*

internal const val TCC_DEMO_HANDLE = "lerolerolero123491432"
internal const val TCC_DEMO_COUNT = 200
internal const val TCC_DEMO_ABILITY = 1000
internal val WATERMELON = ProblemId(4, "A")
private val tccDemoDataset = Regex("tcc-v1-[0-9a-f]{64}")

internal val CloudHandle.isTccDemo: Boolean
    get() = key == cloudHandleKey(TCC_DEMO_HANDLE) && demoDataset?.matches(tccDemoDataset) == true

internal val CloudHandle.isAtTccDemoBaseline: Boolean
    get() = isTccDemo && version == TCC_DEMO_COUNT.toLong() && lastIpsumId == "$demoDataset-$TCC_DEMO_COUNT"

internal fun CloudHandle.isTccImportedAttempt(attempt: CloudIpsum): Boolean =
    isTccDemo && attempt.version in 0 until TCC_DEMO_COUNT.toLong() &&
        attempt.id == "$demoDataset-${attempt.version + 1}"

internal fun tccDemoRecommendation(catalog: List<CodeforcesProblem>): IpsumRecommendation {
    val problem = catalog.singleOrNull { it.id == WATERMELON && it.name == "Watermelon" && it.rating == 800 &&
        it.hasVerifiedOrigin() && requireNotNull(it.origin).startedAtEpochMillis < VerifiedProblemOrigin.CUTOFF_EPOCH_MILLIS }
        ?: error("Não foi possível confirmar o Codeforces 4A — Watermelon no catálogo.")
    return IpsumRecommendation(problem, IpsumCategory.FLUENCY, 800, 800, 800, 0, RecommendationFactors(emptyList(), 0))
}
