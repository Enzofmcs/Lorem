package dev.lorem.app.domain

import kotlin.math.roundToInt

const val TIME_CURVE_POLICY = "time-curve-v1"
const val LEGACY_SCORING_POLICY = "D-012"
const val TIME_SCORING_CAP_MS = 7_200_000L

data class TimeLimits(val t1Millis: Long, val t2Millis: Long, val t3Millis: Long) {
    init { require(0 < t1Millis && t1Millis < t2Millis && t2Millis < t3Millis && t3Millis <= TIME_SCORING_CAP_MS) }
}

enum class TimeBand(val label: String) {
    BONUS("Ganho com bônus"), NORMAL("Ganho normal"), REDUCED("Ganho reduzido"), ZERO("Sem ganho positivo")
}

data class TimeGain(
    val policyVersion: String,
    val band: TimeBand,
    val baseGain: Int,
    val multiplier: Double,
    val timedGain: Int,
    val hintDeduction: Int,
    val gain: Int,
)

/** Independent of acceptance/failure: only applies to the first eligible AC. */
fun applyTimeScoring(baseGain: Int, elapsedMillis: Long, limits: TimeLimits,
    bonusMultiplier: Double, reductionMultiplier: Double, usedHint: Boolean, policyVersion: String): TimeGain {
    require(policyVersion == TIME_CURVE_POLICY) { "Unsupported scoring policy: $policyVersion" }
    require(baseGain in 0..40 && elapsedMillis >= 0)
    require(bonusMultiplier.isFinite() && bonusMultiplier in 1.0..1.25)
    require(reductionMultiplier.isFinite() && reductionMultiplier in 0.0..1.0)
    val band = when {
        elapsedMillis < limits.t1Millis -> TimeBand.BONUS
        elapsedMillis < limits.t2Millis -> TimeBand.NORMAL
        elapsedMillis < limits.t3Millis -> TimeBand.REDUCED
        else -> TimeBand.ZERO
    }
    val factor = when (band) {
        TimeBand.BONUS -> bonusMultiplier
        TimeBand.NORMAL -> 1.0
        TimeBand.REDUCED -> reductionMultiplier
        TimeBand.ZERO -> 0.0
    }
    val timed = (baseGain * factor).roundToInt()
    val hint = if (usedHint) minOf(timed, maxOf(2, (timed * .25).roundToInt())) else 0
    return TimeGain(policyVersion, band, baseGain, factor, timed, hint, timed - hint)
}

/** Immutable start-time decision. Null on old attempts retains their original D-012 contract. */
data class ScoringSnapshot(
    val policyVersion: String,
    val limits: TimeLimits?,
    val bonusMultiplier: Double = 1.25,
    val reductionMultiplier: Double = .5,
    val modelVersion: String?,
    val artifactSha256: String?,
    val ratingSource: String = "codeforces_prior",
    val inputRating: Int?,
    val unavailableReason: String?,
) {
    init {
        require(policyVersion in setOf(LEGACY_SCORING_POLICY, TIME_CURVE_POLICY))
        require(ratingSource == "codeforces_prior")
        require(bonusMultiplier.isFinite() && bonusMultiplier in 1.0..1.25)
        require(reductionMultiplier.isFinite() && reductionMultiplier in 0.0..1.0)
        if (policyVersion == TIME_CURVE_POLICY) {
            require(limits != null && inputRating != null && inputRating > 0)
            require(!modelVersion.isNullOrBlank() && !artifactSha256.isNullOrBlank() && unavailableReason == null)
        } else require(limits == null && !unavailableReason.isNullOrBlank())
    }
}

fun baseLoremGain(ratingBefore: Int, problemRating: Int): Int {
    require(ratingBefore >= 0 && problemRating >= 0)
    val expectation = 1.0 / (1.0 + Math.pow(10.0, (problemRating - ratingBefore) / 400.0))
    return (8.0 + 32.0 * (1.0 - expectation)).roundToInt()
}

/** Presentation uses the saved decomposition, never a new model or a new calculation. */
fun scoringExplanation(snapshot: ScoringSnapshot?, gain: TimeGain?, accepted: Boolean): String = when {
    !accepted -> "Sem AC: penalidade D-012 independente da curva de tempo."
    gain != null -> "${gain.band.label}. Ganho base: ${gain.baseGain}; fator de tempo: ${gain.multiplier}; " +
        "ajuste por tempo: ${gain.timedGain - gain.baseGain}; desconto de dica: ${gain.hintDeduction}; ganho final: ${gain.gain}. " +
        "Política ${gain.policyVersion}."
    snapshot != null -> "Regra D-012 preservada. " + when (snapshot.unavailableReason) {
        "activation_disabled" -> "A pontuação por curvas ainda está em avaliação."
        "missing_prior_rating" -> "Não há Rating Codeforces anterior confiável para selecionar a curva."
        "quantile_not_identified" -> "A curva disponível não identifica todos os marcos de tempo."
        else -> "Não há curva de tempo com suporte suficiente para esta tentativa."
    }
    else -> "Avaliação original D-012 preservada."
}
