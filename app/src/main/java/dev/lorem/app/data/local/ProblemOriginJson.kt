package dev.lorem.app.data.local

import dev.lorem.app.domain.model.VerifiedProblemOrigin
import org.json.JSONObject

internal fun VerifiedProblemOrigin.toStoredJson(): String = JSONObject()
    .put("contestId", contestId)
    .put("startedAtEpochMillis", startedAtEpochMillis)
    .put("verifiedAtEpochMillis", verifiedAtEpochMillis)
    .put("source", source)
    .put("eligibilityVersion", eligibilityVersion)
    .toString()

internal fun String.toProblemOrigin(): VerifiedProblemOrigin? = runCatching {
    val json = JSONObject(this)
    VerifiedProblemOrigin(
        json.getLong("contestId"), json.getLong("startedAtEpochMillis"),
        json.getLong("verifiedAtEpochMillis"), json.getString("source"), json.getString("eligibilityVersion"),
    )
}.getOrNull()
