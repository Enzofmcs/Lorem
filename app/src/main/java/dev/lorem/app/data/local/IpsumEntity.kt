package dev.lorem.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId

@Entity(
    tableName = "ipsums",
    indices = [Index(value = ["activeSlot"], unique = true)],
)
data class IpsumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerHandle: String,
    val contestId: Long,
    val problemIndex: String,
    val problemName: String,
    val problemTags: String,
    val initialLoremRating: Int,
    val category: String,
    val desiredRatingMin: Int,
    val desiredRatingMax: Int,
    val selectedRating: Int,
    val fallbackDistance: Int,
    val startedAtEpochMillis: Long,
    val hintRevealedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val errorCount: Int,
    val status: String,
    val activeSlot: Int?,
)

fun Ipsum.toEntity() = IpsumEntity(
    id = id,
    ownerHandle = ownerHandle,
    contestId = problem.id.contestId,
    problemIndex = problem.id.index,
    problemName = problem.name,
    problemTags = problem.tags.sorted().joinToString("\u0000"),
    initialLoremRating = initialLoremRating,
    category = category.name,
    desiredRatingMin = desiredRatingMin,
    desiredRatingMax = desiredRatingMax,
    selectedRating = selectedRating,
    fallbackDistance = fallbackDistance,
    startedAtEpochMillis = startedAtEpochMillis,
    hintRevealedAtEpochMillis = hintRevealedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    errorCount = errorCount,
    status = status.name,
    activeSlot = if (status == IpsumStatus.ACTIVE) 1 else null,
)

fun IpsumEntity.toDomain() = Ipsum(
    id = id,
    ownerHandle = ownerHandle,
    problem = CodeforcesProblem(
        id = ProblemId(contestId, problemIndex),
        name = problemName,
        rating = selectedRating,
        tags = problemTags.takeIf(String::isNotEmpty)?.split("\u0000")?.toSet().orEmpty(),
    ),
    initialLoremRating = initialLoremRating,
    category = IpsumCategory.valueOf(category),
    desiredRatingMin = desiredRatingMin,
    desiredRatingMax = desiredRatingMax,
    selectedRating = selectedRating,
    fallbackDistance = fallbackDistance,
    startedAtEpochMillis = startedAtEpochMillis,
    hintRevealedAtEpochMillis = hintRevealedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    errorCount = errorCount,
    status = IpsumStatus.valueOf(status),
)
