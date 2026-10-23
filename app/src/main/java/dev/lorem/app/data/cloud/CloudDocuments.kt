package dev.lorem.app.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import dev.lorem.app.domain.IpsumRecommendation
import dev.lorem.app.domain.ScoringSnapshot
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import java.util.Date

internal fun timestamp(millis: Long) = Timestamp(Date(millis))
internal fun DocumentSnapshot.cloudHandle() = CloudHandle(id, getString("handle")!!,
    getLong("loremRating")!!.toInt(), getLong("consolidatedRating")?.toInt(), getLong("progressVersion")!!,
    getLong("bindingVersion")!!, getLong("duelRatingMicros"))

internal fun snapshotOf(recommendation: IpsumRecommendation, rating: Int, handle: String, cf: Int?, cfAt: Long?, scoring: ScoringSnapshot? = null, duel: dev.lorem.app.domain.VirtualDuelSnapshot? = null): Map<String, Any?> {
    val p = recommendation.problem
    return mapOf(
        "handle" to handle, "initialRating" to rating, "contestId" to p.id.contestId, "index" to p.id.index,
        "name" to p.name, "rating" to p.rating, "tags" to p.tags.sorted(),
        "originAt" to p.origin!!.startedAtEpochMillis, "originVerifiedAt" to p.origin.verifiedAtEpochMillis,
        "originSource" to p.origin.source, "originVersion" to p.origin.eligibilityVersion,
        "category" to recommendation.category.name, "desiredMin" to recommendation.desiredRatingMin,
        "desiredMax" to recommendation.desiredRatingMax, "fallback" to recommendation.fallbackDistance,
        "cfRating" to cf, "cfObservedAt" to cfAt,
        "factors" to recommendation.factors.tags.map { mapOf("tag" to it.tag, "exposure" to it.exposure,
            "attempts" to it.attempts, "accepted" to it.accepted, "hints" to it.hints) },
        "recentOverlap" to recommendation.factors.recentOverlap,
    ) + (scoring?.let { mapOf("scoring" to it.toDocument()) } ?: emptyMap()) + (duel?.let { mapOf("virtualDuel" to it.toDocument()) } ?: emptyMap())
}

internal fun resultOf(ipsum: Ipsum): Map<String, Any?> = mapOf(
    "endedAt" to timestamp(ipsum.endedAtEpochMillis!!), "accepted" to (ipsum.status == IpsumStatus.COMPLETED),
    "errors" to ipsum.errorCount, "failure" to ipsum.failureReason?.name, "delta" to ipsum.ratingDelta,
    "finalRating" to ipsum.finalLoremRating, "expectedTime" to ipsum.expectedTimeMillis,
) + (ipsum.virtualDuelResult?.let { mapOf("virtualDuel" to it.toDocument()) } ?: emptyMap()) + (ipsum.timeGain?.let { mapOf("timeGain" to it.toDocument()) } ?: emptyMap())

internal fun DocumentSnapshot.cloudIpsum(): CloudIpsum {
    val s = get("snapshot") as Map<*, *>
    fun number(k: String) = (s[k] as Number).toLong()
    val startedTimestamp = getTimestamp("startedAt")!!
    val started = startedTimestamp.toDate().time
    val result = get("result") as? Map<*, *>
    val outside = getTimestamp("solvedOutsideAt")?.toDate()?.time
    val accepted = result?.get("accepted") == true
    val p = CodeforcesProblem(ProblemId(number("contestId"), s["index"] as String), s["name"] as String,
        number("rating").toInt(), (s["tags"] as List<*>).filterIsInstance<String>().toSet(),
        VerifiedProblemOrigin(number("contestId"), number("originAt"), number("originVerifiedAt"),
            s["originSource"] as String, s["originVersion"] as String))
    val factors = (s["factors"] as List<*>).map { value ->
        val f = value as Map<*, *>
        fun n(key: String) = (f[key] as Number).toInt()
        TagNeed(f["tag"] as String, n("exposure"), n("attempts"), n("accepted"), n("hints"))
    }
    return CloudIpsum(id, getString("handleKey")!!, getLong("version")!!,
        CloudIpsumState.valueOf(getString("state")!!), Ipsum(
            // Stable unique sequence per handle, also used by existing consolidation/statistics functions.
            id = getLong("version")!! + 1, ownerHandle = s["handle"] as String, problem = p,
            initialLoremRating = number("initialRating").toInt(), category = IpsumCategory.valueOf(s["category"] as String),
            desiredRatingMin = number("desiredMin").toInt(), desiredRatingMax = number("desiredMax").toInt(),
            selectedRating = number("rating").toInt(), fallbackDistance = number("fallback").toInt(),
            startedAtEpochMillis = started, hintRevealedAtEpochMillis = getTimestamp("hintAt")?.toDate()?.time,
            endedAtEpochMillis = (result?.get("endedAt") as? Timestamp)?.toDate()?.time,
            errorCount = (result?.get("errors") as? Number)?.toInt() ?: 0,
            failureReason = (result?.get("failure") as? String)?.let(IpsumFailureReason::valueOf),
            ratingDelta = (result?.get("delta") as? Number)?.toInt(),
            finalLoremRating = (result?.get("finalRating") as? Number)?.toInt(),
            expectedTimeMillis = (result?.get("expectedTime") as? Number)?.toLong(),
            solvedOutsideAtEpochMillis = outside,
            status = if (result == null) IpsumStatus.ACTIVE else if (accepted) IpsumStatus.COMPLETED
                else if (outside != null) IpsumStatus.SOLVED_OUTSIDE else IpsumStatus.PENDING,
            recommendationFactors = RecommendationFactors(factors, number("recentOverlap").toInt()),
            initialCodeforcesRating = (s["cfRating"] as? Number)?.toInt(),
            initialCodeforcesRatingObservedAtEpochMillis = (s["cfObservedAt"] as? Number)?.toLong(),
            studyRecordingVersion = CLOUD_RULE_VERSION,
            scoringSnapshot = if (s.containsKey("scoring")) scoringSnapshot(s["scoring"] as Map<*, *>) else null,
            virtualDuelSnapshot = (s["virtualDuel"] as? Map<*, *>)?.let(::virtualDuelSnapshot),
            virtualDuelResult = (result?.get("virtualDuel") as? Map<*, *>)?.let(::virtualDuelResult),
            timeGain = (result?.get("timeGain") as? Map<*, *>)?.let(::timeGain),
        ), getTimestamp("cutoffAt")?.toDate()?.time,
        getString("closeReason")?.let(CloudCloseReason::valueOf),
        getTimestamp("processedAt")?.toDate()?.time,
        startedTimestamp.seconds * 1000 + if (startedTimestamp.nanoseconds > 0) 1000 else 0,
        getLong("bindingVersion")!!)
}
