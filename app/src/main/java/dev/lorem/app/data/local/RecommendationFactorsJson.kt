package dev.lorem.app.data.local

import dev.lorem.app.domain.model.RecommendationFactors
import dev.lorem.app.domain.model.TagNeed
import org.json.JSONArray
import org.json.JSONObject

internal fun RecommendationFactors.toStoredJson(): String = JSONObject()
    .put("recentOverlap", recentOverlap)
    .put("tags", JSONArray().apply {
        tags.forEach { tag -> put(JSONObject().put("tag", tag.tag).put("exposure", tag.exposure)
            .put("attempts", tag.attempts).put("accepted", tag.accepted).put("hints", tag.hints)) }
    }).toString()

internal fun String.toRecommendationFactors(): RecommendationFactors? = runCatching {
    val json = JSONObject(this)
    val tags = json.getJSONArray("tags")
    RecommendationFactors((0 until tags.length()).map { index ->
        val tag = tags.getJSONObject(index)
        TagNeed(tag.getString("tag"), tag.getInt("exposure"), tag.getInt("attempts"), tag.getInt("accepted"), tag.getInt("hints"))
    }, json.getInt("recentOverlap"))
}.getOrNull()
