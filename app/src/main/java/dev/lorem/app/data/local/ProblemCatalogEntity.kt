package dev.lorem.app.data.local

import androidx.room.Entity
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.ProblemId

@Entity(tableName = "problem_catalog", primaryKeys = ["contestId", "problemIndex"])
data class ProblemCatalogEntity(
    val contestId: Long,
    val problemIndex: String,
    val name: String,
    val rating: Int,
    val tags: String,
)

private const val TAG_SEPARATOR = "\u0000"

fun CodeforcesProblem.toCatalogEntity() = ProblemCatalogEntity(
    contestId = id.contestId,
    problemIndex = id.index,
    name = name,
    rating = requireNotNull(rating),
    tags = tags.sorted().joinToString(TAG_SEPARATOR),
)

fun ProblemCatalogEntity.toDomain() = CodeforcesProblem(
    id = ProblemId(contestId, problemIndex),
    name = name,
    rating = rating,
    tags = tags.takeIf(String::isNotEmpty)?.split(TAG_SEPARATOR)?.toSet().orEmpty(),
)
