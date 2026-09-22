package dev.lorem.app.domain.model

data class ProblemId(
    val contestId: Long,
    val index: String,
) {
    init {
        require(contestId > 0) { "contestId must be positive" }
        require(index.isNotBlank()) { "index must not be blank" }
    }

    override fun toString(): String = "$contestId$index"
}

data class CodeforcesProblem(
    val id: ProblemId,
    val name: String,
    val rating: Int?,
    val tags: Set<String>,
)
