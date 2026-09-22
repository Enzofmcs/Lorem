package dev.lorem.app.domain.model

data class ProblemHistory(
    val problemId: ProblemId,
    val attempted: Boolean,
    val hasAcceptedSubmission: Boolean,
)
