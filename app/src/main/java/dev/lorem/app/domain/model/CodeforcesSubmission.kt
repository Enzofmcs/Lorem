package dev.lorem.app.domain.model

import java.time.Instant

data class CodeforcesSubmission(
    val id: Long,
    val problemId: ProblemId,
    val verdict: String?,
    val createdAt: Instant,
) {
    init {
        require(id > 0) { "id must be positive" }
    }
}
