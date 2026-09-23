package dev.lorem.app.domain.model

enum class IpsumCategory {
    FLUENCY,
    CURRENT_LEVEL,
    CHALLENGE,
}

enum class IpsumStatus {
    ACTIVE,
    COMPLETED,
    PENDING,
}

data class Ipsum(
    val id: Long,
    val ownerHandle: String,
    val problem: CodeforcesProblem,
    val initialLoremRating: Int,
    val category: IpsumCategory,
    val desiredRatingMin: Int,
    val desiredRatingMax: Int,
    val selectedRating: Int,
    val fallbackDistance: Int,
    val startedAtEpochMillis: Long,
    val status: IpsumStatus,
)
