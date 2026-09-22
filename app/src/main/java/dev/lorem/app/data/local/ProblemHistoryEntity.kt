package dev.lorem.app.data.local

import androidx.room.Entity
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId

@Entity(
    tableName = "problem_history",
    primaryKeys = ["contestId", "problemIndex"],
)
data class ProblemHistoryEntity(
    val contestId: Long,
    val problemIndex: String,
    val attempted: Boolean,
    val hasAcceptedSubmission: Boolean,
)

fun ProblemHistory.toEntity() = ProblemHistoryEntity(
    contestId = problemId.contestId,
    problemIndex = problemId.index,
    attempted = attempted,
    hasAcceptedSubmission = hasAcceptedSubmission,
)

fun ProblemHistoryEntity.toDomain() = ProblemHistory(
    problemId = ProblemId(contestId = contestId, index = problemIndex),
    attempted = attempted,
    hasAcceptedSubmission = hasAcceptedSubmission,
)
