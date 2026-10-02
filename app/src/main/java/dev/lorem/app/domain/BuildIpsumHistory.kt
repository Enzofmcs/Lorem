package dev.lorem.app.domain

import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId

enum class IpsumHistorySituation(val label: String) {
    SOLVED_IN_IPSUM("Resolvidos em Ipsums"),
    PENDING("Pendentes"),
    SOLVED_OUTSIDE_IPSUM("Resolvidos fora de Ipsums"),
}

data class IpsumHistoryFilters(
    val rating: Int? = null,
    val tag: String? = null,
)

data class IpsumHistoryItem(
    val ipsum: Ipsum,
    val situation: IpsumHistorySituation,
)

/**
 * Builds the current, mutually exclusive view of finished Ipsums.
 *
 * [solvedOutsideIpsum] must contain only persisted evidence produced by the later
 * upsolve synchronization. Until that evidence exists, callers pass an empty set.
 */
fun buildIpsumHistory(
    ipsums: List<Ipsum>,
    solvedOutsideIpsum: Set<ProblemId> = emptySet(),
): List<IpsumHistoryItem> = ipsums
    .asSequence()
    .filter { it.status != IpsumStatus.ACTIVE }
    .groupBy { it.problem.id }
    .mapNotNull { (problemId, attempts) ->
        val latest = attempts.maxWithOrNull(
            compareBy<Ipsum> { it.endedAtEpochMillis ?: Long.MIN_VALUE }.thenBy { it.id },
        ) ?: return@mapNotNull null
        val situation = when {
            latest.status == IpsumStatus.PENDING && problemId in solvedOutsideIpsum ->
                IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM
            latest.status == IpsumStatus.COMPLETED -> IpsumHistorySituation.SOLVED_IN_IPSUM
            latest.status == IpsumStatus.PENDING -> IpsumHistorySituation.PENDING
            else -> return@mapNotNull null
        }
        IpsumHistoryItem(latest, situation)
    }
    .sortedByDescending { it.ipsum.endedAtEpochMillis ?: it.ipsum.startedAtEpochMillis }

fun filterIpsumHistory(
    items: List<IpsumHistoryItem>,
    situation: IpsumHistorySituation,
    filters: IpsumHistoryFilters,
): List<IpsumHistoryItem> = items.filter { item ->
    item.situation == situation &&
        (filters.rating == null || item.ipsum.selectedRating == filters.rating) &&
        (filters.tag == null || filters.tag in item.ipsum.problem.tags)
}
