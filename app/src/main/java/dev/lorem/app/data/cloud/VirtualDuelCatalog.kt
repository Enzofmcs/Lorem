package dev.lorem.app.data.cloud

import android.content.res.AssetManager
import android.util.Log
import dev.lorem.app.domain.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/** Frozen full model always stays available for old attempts, including after disabling new starts. */
class VirtualDuelCatalog(json: String) {
    private val artifact: JSONObject

    init {
        // Git checkouts may convert LF to CRLF. Verify the canonical bytes used by the saved snapshots.
        val canonical = json.replace("\r\n", "\n")
        val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        require(hash == DUEL_SHA) { "SHA-256 inválido para o modelo Dolor." }
        artifact = JSONObject(canonical)
        require(artifact.optString("policy") == VIRTUAL_DUEL_POLICY && artifact.optString("model_version") == DUEL_MODEL) {
            "Versão incompatível do modelo Dolor."
        }
    }

    companion object {
        internal fun load(assets: AssetManager): VirtualDuelCatalog? = try {
            VirtualDuelCatalog(assets.open("virtual-duel-v1.json").bufferedReader().use { it.readText() })
        } catch (error: IOException) {
            unavailable(error)
        } catch (error: IllegalArgumentException) {
            unavailable(error)
        } catch (error: JSONException) {
            unavailable(error)
        }

        private fun unavailable(error: Exception): VirtualDuelCatalog? {
            Log.e("LoremDolor", "Não foi possível carregar o modelo do Rating Dolor.", error)
            return null
        }
    }

    fun availability(ratingMicros: Long, difficulty: Int): DuelAvailability {
        if (ratingMicros !in DUEL_RATING_DOMAIN || difficulty !in 800..3500)
            return DuelAvailability.Unavailable("out_of_domain")
        val delta = difficulty * DUEL_SCALE - ratingMicros
        val low = Math.floorDiv(delta, 400 * DUEL_SCALE).toInt() * 400
        if (low !in -1200..1200) return DuelAvailability.Unavailable("unsupported_region")
        return DuelAvailability.Available(VirtualDuelSnapshot(ratingMicros, difficulty, low))
    }
    fun select(ratingMicros: Long, difficulty: Int): VirtualDuelSnapshot = when (val selected = availability(ratingMicros, difficulty)) {
        is DuelAvailability.Available -> selected.snapshot
        is DuelAvailability.Unavailable -> throw IllegalArgumentException("Avaliação Dolor indisponível para este nível e problema.")
    }
    fun evaluate(snapshot: VirtualDuelSnapshot, elapsedMillis: Long, accepted: Boolean): VirtualDuelResult {
        val curves = artifact.getJSONArray("curves")
        val entry = (0 until curves.length()).map { curves.getJSONObject(it) }.single { it.getInt("delta_min") == snapshot.curveMin }
        require(entry.getInt("attempts") >= 50 && entry.getInt("events") >= 20 && entry.getLong("followup_ms") >= snapshot.horizonMillis)
        val t = entry.getJSONArray("times_ms"); val f = entry.getJSONArray("cdf_ppm")
        val curve = DuelCurve((0 until t.length()).map { t.getLong(it) }, (0 until f.length()).map { f.getLong(it) })
        return calculateVirtualDuel(snapshot, curve, elapsedMillis, accepted)
    }
}

internal fun VirtualDuelSnapshot.toDocument(): Map<String, Any> = mapOf("policy" to policy, "model" to model,
    "sha256" to sha256, "ratingMicros" to ratingMicros, "difficulty" to difficulty, "curveMin" to curveMin,
    "k" to k, "horizonMillis" to horizonMillis, "assistance" to assistance)
internal fun virtualDuelSnapshot(d: Map<*, *>) = VirtualDuelSnapshot((d["ratingMicros"] as Number).toLong(),
    (d["difficulty"] as Number).toInt(), (d["curveMin"] as Number).toInt(), (d["k"] as Number).toInt(),
    (d["horizonMillis"] as Number).toLong(), d["policy"] as String, d["model"] as String, d["sha256"] as String,
    d["assistance"] as String)
internal fun VirtualDuelResult.toDocument(): Map<String, Long> = mapOf("beforeMicros" to beforeMicros,
    "deltaMicros" to deltaMicros, "afterMicros" to afterMicros, "cdfLeft" to cdfLeft, "cdfAt" to cdfAt, "cdfHorizon" to cdfHorizon)
internal fun virtualDuelResult(d: Map<*, *>) = VirtualDuelResult((d["beforeMicros"] as Number).toLong(),
    (d["deltaMicros"] as Number).toLong(), (d["afterMicros"] as Number).toLong(), (d["cdfLeft"] as Number).toLong(),
    (d["cdfAt"] as Number).toLong(), (d["cdfHorizon"] as Number).toLong())
