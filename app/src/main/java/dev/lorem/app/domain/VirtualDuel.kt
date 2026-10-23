package dev.lorem.app.domain

const val VIRTUAL_DUEL_POLICY = "virtual-duel-v1"
const val DUEL_SCALE = 1_000_000L
const val DUEL_MODEL = "km-delta-isotonic-20261020-v1"
const val DUEL_SHA = "69954b61bc111b2b2b1c33efef7e66978a74a378244ef0f41f834f9d6a8daea3"

/** Separate provisional scale; neither a conversion nor equivalence with Codeforces. */
data class VirtualDuelSnapshot(
    val ratingMicros: Long,
    val difficulty: Int,
    val curveMin: Int,
    val k: Int = 32,
    val horizonMillis: Long = TIME_SCORING_CAP_MS,
    val policy: String = VIRTUAL_DUEL_POLICY,
    val model: String = DUEL_MODEL,
    val sha256: String = DUEL_SHA,
    val assistance: String = "unassisted",
) {
    init {
        require(policy == VIRTUAL_DUEL_POLICY && model == DUEL_MODEL && sha256 == DUEL_SHA)
        require(k == 32 || k == 64)
        require(horizonMillis == TIME_SCORING_CAP_MS && assistance == "unassisted")
        require(ratingMicros in 21 * DUEL_SCALE..3857 * DUEL_SCALE && difficulty in 800..3500)
        require(curveMin in -1200..1200 && curveMin % 400 == 0)
        require(difficulty * DUEL_SCALE - ratingMicros in curveMin * DUEL_SCALE until (curveMin + 400) * DUEL_SCALE)
    }
}

data class VirtualDuelResult(val beforeMicros: Long, val deltaMicros: Long, val afterMicros: Long,
    val cdfLeft: Long, val cdfAt: Long, val cdfHorizon: Long) {
    init {
        require(cdfLeft in 0..cdfAt && cdfAt <= cdfHorizon && cdfHorizon <= DUEL_SCALE)
        require(afterMicros == beforeMicros + deltaMicros)
    }
}

sealed interface DuelAvailability {
    data class Available(val snapshot: VirtualDuelSnapshot) : DuelAvailability
    data class Unavailable(val reason: String) : DuelAvailability
}

/** All products fit Long and K/2 is integral. Fractions survive across attempts. */
fun virtualDuelDelta(left: Long, at: Long, horizon: Long, k: Int, accepted: Boolean): Long {
    require(k == 32 || k == 64)
    require(left in 0..at && at <= horizon && horizon <= DUEL_SCALE)
    return (k / 2) * if (accepted) DUEL_SCALE - left - at else -horizon
}

data class DuelCurve(val timesMillis: List<Long>, val cdfPpm: List<Long>) {
    init {
        require(timesMillis.size == cdfPpm.size && timesMillis.isNotEmpty())
        require(timesMillis.first() >= 0 && timesMillis.last() <= TIME_SCORING_CAP_MS)
        require(timesMillis.zipWithNext().all { (a, b) -> a < b })
        require(cdfPpm.all { it in 0..DUEL_SCALE } && cdfPpm.zipWithNext().all { (a, b) -> a <= b })
    }
    fun pair(elapsedMillis: Long): Pair<Long, Long> {
        require(elapsedMillis in 0..TIME_SCORING_CAP_MS)
        val index = timesMillis.binarySearch(elapsedMillis)
        val through = if (index >= 0) index else -index - 2
        val before = if (index >= 0) index - 1 else through
        return (cdfPpm.getOrNull(before) ?: 0L) to (cdfPpm.getOrNull(through) ?: 0L)
    }
}

fun calculateVirtualDuel(snapshot: VirtualDuelSnapshot, curve: DuelCurve, elapsedMillis: Long, accepted: Boolean): VirtualDuelResult {
    require(elapsedMillis in 0..snapshot.horizonMillis)
    val (left, at) = curve.pair(elapsedMillis)
    val horizon = curve.pair(snapshot.horizonMillis).second
    val delta = virtualDuelDelta(left, at, horizon, snapshot.k, accepted)
    return VirtualDuelResult(snapshot.ratingMicros, delta, snapshot.ratingMicros + delta, left, at, horizon)
}

fun VirtualDuelResult.explanation(): String = java.lang.String.format(java.util.Locale.forLanguageTag("pt-BR"),
    "Rating experimental: %.6f → %.6f (%+.6f). Duelo virtual; referência de tempo congelada no início. " +
        "Escala provisória independente; sem equivalência validada com Codeforces. Prazo de 120 min, sem dicas.",
    beforeMicros.toDouble() / DUEL_SCALE, afterMicros.toDouble() / DUEL_SCALE, deltaMicros.toDouble() / DUEL_SCALE)
