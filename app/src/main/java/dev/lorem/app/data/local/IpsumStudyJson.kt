package dev.lorem.app.data.local

import dev.lorem.app.domain.IpsumStudyExport
import dev.lorem.app.domain.model.IpsumStatus
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

fun IpsumStudyExport.toStudyJson(applicationVersion: String?): String {
    require(dataKind in setOf("ipsum_local", "ipsum_cloud", "synthetic"))
    fun json(fields: Map<String, Any?>) = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }
    val rows = JSONArray()
    attempts.filter { it.status != IpsumStatus.ACTIVE }.forEach { ipsum ->
        val sample = MessageDigest.getInstance("SHA-256")
            .digest("$participantKey:${if (dataKind == "ipsum_cloud") sampleScope + ":" else ""}${ipsum.id}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val origin = ipsum.problem.origin
        rows.put(json(mapOf(
            "sample_id" to sample,
            "problem_contest_id" to ipsum.problem.id.contestId,
            "problem_index" to ipsum.problem.id.index,
            "started_at_epoch_ms" to ipsum.startedAtEpochMillis,
            "ended_at_epoch_ms" to ipsum.endedAtEpochMillis,
            "first_ac_at_epoch_ms" to ipsum.endedAtEpochMillis.takeIf { ipsum.status == IpsumStatus.COMPLETED },
            "upsolve_ac_at_epoch_ms" to ipsum.solvedOutsideAtEpochMillis,
            "status" to ipsum.status.name,
            "failure_reason" to ipsum.failureReason?.name,
            "hint_revealed_at_epoch_ms" to ipsum.hintRevealedAtEpochMillis,
            "hint_used" to (if (ipsum.hintRevealedAtEpochMillis != null) true else
                false.takeIf { ipsum.studyRecordingVersion in setOf("ipsum-study-v1", "spark-2h-v1") }),
            "problem_rating" to ipsum.selectedRating,
            "cf_rating_before" to ipsum.initialCodeforcesRating,
            "cf_rating_observed_at_epoch_ms" to ipsum.initialCodeforcesRatingObservedAtEpochMillis,
            "cf_rating_source" to "codeforces:user.info".takeIf { ipsum.initialCodeforcesRating != null },
            "lorem_rating_before" to ipsum.initialLoremRating,
            "recording_version" to ipsum.studyRecordingVersion,
            "rating_rule_version" to (ipsum.scoringSnapshot?.policyVersion ?: "D-012").takeIf { ipsum.studyRecordingVersion in setOf("ipsum-study-v1", "spark-2h-v1") },
            "eligibility_version" to origin?.eligibilityVersion,
            "origin_started_at_epoch_ms" to origin?.startedAtEpochMillis,
            "origin_source" to origin?.source,
            "origin_verified_at_epoch_ms" to origin?.verifiedAtEpochMillis,
            "time_scoring_snapshot" to ipsum.scoringSnapshot?.let { snapshot -> JSONObject().apply {
                put("policy_version", snapshot.policyVersion).put("model_version", snapshot.modelVersion ?: JSONObject.NULL)
                put("artifact_sha256", snapshot.artifactSha256 ?: JSONObject.NULL).put("rating_source", snapshot.ratingSource)
                put("input_rating", snapshot.inputRating ?: JSONObject.NULL).put("fallback_reason", snapshot.unavailableReason ?: JSONObject.NULL)
                put("bonus_multiplier", snapshot.bonusMultiplier).put("reduction_multiplier", snapshot.reductionMultiplier)
                put("limits_ms", snapshot.limits?.let { JSONArray(listOf(it.t1Millis, it.t2Millis, it.t3Millis)) } ?: JSONObject.NULL)
            } },
            "time_scoring_result" to ipsum.timeGain?.let { gain -> JSONObject().apply {
                put("band", gain.band.name).put("base_gain", gain.baseGain).put("multiplier", gain.multiplier)
                put("timed_gain", gain.timedGain).put("hint_deduction", gain.hintDeduction).put("gain", gain.gain)
            } },
            "virtual_duel_snapshot" to ipsum.virtualDuelSnapshot?.let { d -> json(mapOf(
                "policy" to d.policy, "model" to d.model, "sha256" to d.sha256, "rating_micros" to d.ratingMicros,
                "difficulty" to d.difficulty, "curve_min" to d.curveMin, "k" to d.k,
                "horizon_ms" to d.horizonMillis, "assistance" to d.assistance)) },
            "virtual_duel_result" to ipsum.virtualDuelResult?.let { r -> json(mapOf(
                "before_micros" to r.beforeMicros, "delta_micros" to r.deltaMicros, "after_micros" to r.afterMicros,
                "cdf_left_ppm" to r.cdfLeft, "cdf_at_ppm" to r.cdfAt, "cdf_horizon_ppm" to r.cdfHorizon)) },
            "interruptions" to null,
            "wall_clock_only" to true,
        )))
    }
    if (dataKind == "ipsum_cloud") require(sampleScope.isNotBlank())
    val envelope = json(mapOf("schema_version" to (if (dataKind == "ipsum_cloud") 2 else 1), "export_version" to (if (dataKind == "ipsum_cloud") "ipsum-cloud-v2" else "ipsum-local-v1"), "data_kind" to dataKind,
        "participant_key" to participantKey, "exported_at_epoch_ms" to exportedAtEpochMillis,
        "application_version" to applicationVersion, "active_omitted" to activeOmitted,
        "attempts" to rows,
        "note" to "Pseudonymous local observations; pauses unknown; no transport evaluation"))
    if (dataKind == "ipsum_cloud") {
        envelope.put("identity_version", "installation-handle-v1")
            .put("initial_rating_policy", "binding-800-v1").put("time_limit_ms", 7200000)
            .put("clock_policy", "server-start-monotonic-v1").put("verification_trust", "android-codeforces-client-v1")
            .put("cohort_policy", "separate-cloud-protocol; repeated handles across installations require study review")
    }
    return envelope.toString(2)
}
