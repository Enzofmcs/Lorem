package dev.lorem.app.data.cloud

import dev.lorem.app.domain.*

internal fun ScoringSnapshot.toDocument(): Map<String, Any?> = mapOf(
    "policyVersion" to policyVersion, "modelVersion" to modelVersion, "artifactSha256" to artifactSha256,
    "ratingSource" to ratingSource, "inputRating" to inputRating, "unavailableReason" to unavailableReason,
    "bonusMultiplier" to bonusMultiplier, "reductionMultiplier" to reductionMultiplier,
    "limits" to limits?.let { mapOf("t1" to it.t1Millis, "t2" to it.t2Millis, "t3" to it.t3Millis) })

internal fun scoringSnapshot(document: Map<*, *>): ScoringSnapshot {
    val limits = (document["limits"] as? Map<*, *>)?.let {
        TimeLimits((it["t1"] as Number).toLong(), (it["t2"] as Number).toLong(), (it["t3"] as Number).toLong())
    }
    return ScoringSnapshot(document["policyVersion"] as String, limits,
        (document["bonusMultiplier"] as Number).toDouble(), (document["reductionMultiplier"] as Number).toDouble(),
        document["modelVersion"] as String?, document["artifactSha256"] as String?, document["ratingSource"] as String,
        (document["inputRating"] as? Number)?.toInt(), document["unavailableReason"] as String?)
}

internal fun TimeGain.toDocument(): Map<String, Any?> = mapOf("policyVersion" to policyVersion,
    "band" to band.name, "baseGain" to baseGain, "multiplier" to multiplier, "timedGain" to timedGain,
    "hintDeduction" to hintDeduction, "gain" to gain)

internal fun timeGain(document: Map<*, *>): TimeGain = TimeGain(document["policyVersion"] as String,
    TimeBand.valueOf(document["band"] as String), (document["baseGain"] as Number).toInt(),
    (document["multiplier"] as Number).toDouble(), (document["timedGain"] as Number).toInt(),
    (document["hintDeduction"] as Number).toInt(), (document["gain"] as Number).toInt())
