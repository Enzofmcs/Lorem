package dev.lorem.app.data.cloud

import dev.lorem.app.domain.*
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.ceil

/** Bundled, offline artifact; source is explicit and activation is always an independent gate. */
class TimeCurveCatalog(private val json: String, private val activationEnabled: Boolean = false) {
    fun select(cfRating: Int?, problemRating: Int): ScoringSnapshot = try {
        selectChecked(cfRating, problemRating)
    } catch (_: org.json.JSONException) { invalidArtifact(cfRating)
    } catch (_: IllegalArgumentException) { invalidArtifact(cfRating) }

    private fun invalidArtifact(cfRating: Int?) = ScoringSnapshot(LEGACY_SCORING_POLICY, null,
        modelVersion = null, artifactSha256 = null, inputRating = cfRating, unavailableReason = "invalid_artifact")

    private fun selectChecked(cfRating: Int?, problemRating: Int): ScoringSnapshot {
        val artifact = JSONObject(json)
        val model = artifact.optString("model_version").takeIf { it.isNotBlank() }
        val sha = MessageDigest.getInstance("SHA-256").digest(json.trim().toByteArray()).joinToString("") { "%02x".format(it) }
        fun fallback(reason: String) = ScoringSnapshot(LEGACY_SCORING_POLICY, null,
            modelVersion = model, artifactSha256 = sha, inputRating = cfRating, unavailableReason = reason)
        if (artifact.optString("artifact_version") != "time-curves-v1" || model != "kaplan-meier-v1" ||
            artifact.optInt("schema_version") != 1 || artifact.optString("policy_version") != TIME_CURVE_POLICY ||
            artifact.optLong("cap_ms") != TIME_SCORING_CAP_MS) return fallback("unsupported_artifact")
        if (artifact.optString("rating_source") != "codeforces_prior") return fallback("rating_scale_mismatch")
        if (cfRating == null || cfRating <= 0) return fallback("missing_prior_rating")
        val delta = problemRating - cfRating
        artifact.optJSONObject("domain")?.let { domain ->
            for ((key, value) in listOf("old_rating" to cfRating, "problem_rating" to problemRating, "delta" to delta)) {
                val range = domain.getJSONArray(key)
                if (value < range.getInt(0) || value > range.getInt(1)) return fallback("out_of_domain")
            }
        }
        val regions = artifact.getJSONArray("regions")
        val matches = (0 until regions.length()).map { regions.getJSONObject(it) }.filter { r ->
            if (r.has("old_rating")) r.getInt("old_rating") == cfRating && r.getInt("problem_rating") == problemRating
            else r.getInt("delta_min") <= delta && delta < r.getInt("delta_max_exclusive")
        }
        if (matches.size != 1) return fallback("unsupported_region")
        val r = matches.single()
        if (!r.isNull("unavailable_reason")) return fallback(r.getString("unavailable_reason"))
        val limits = try {
            val b = r.getJSONObject("limits")
            val candidate = TimeLimits(b.getLong("t1_ms"), b.getLong("t2_ms"), b.getLong("t3_ms"))
            val q = r.getJSONObject("quantiles_minutes")
            require(ceil(q.getDouble("0.5") * 60000).toLong() == candidate.t1Millis)
            require(ceil(q.getDouble("0.75") * 60000).toLong() == candidate.t2Millis)
            require(minOf(TIME_SCORING_CAP_MS, ceil(q.getDouble("0.9") * 60000).toLong()) == candidate.t3Millis)
            val support = r.getJSONObject("support")
            require(support.getInt("attempts") >= support.getInt("minimum_attempts"))
            require(support.getInt("events") >= support.getInt("minimum_events"))
            require(listOf("0.5", "0.75", "0.9").all {
                q.getDouble(it).isFinite() && q.getDouble(it) * 60 <= support.getDouble("max_followup_seconds")
            })
            candidate
        } catch (_: IllegalArgumentException) { return fallback("invalid_limits_or_support")
        } catch (_: org.json.JSONException) { return fallback("invalid_limits_or_support") }
        // Exporting/replacing a model must never turn on scoring by itself.
        if (!activationEnabled || artifact.optString("activation") != "enabled") return fallback("activation_disabled")
        return ScoringSnapshot(TIME_CURVE_POLICY, limits, modelVersion = model,
            artifactSha256 = sha, inputRating = cfRating, unavailableReason = null)
    }
}
