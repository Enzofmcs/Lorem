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

enum class IpsumFailureReason(val label: String) {
    STATEMENT_NOT_UNDERSTOOD("Não entendi o enunciado."),
    LOGIC_NOT_FOUND("Não encontrei a lógica."),
    CONTENT_UNKNOWN("Não conhecia o conteúdo."),
    IMPLEMENTATION_NOT_COMPLETED("Encontrei a solução, mas não consegui implementar."),
    IMPLEMENTATION_ERROR("Tive erro de implementação."),
    TIME_EXPIRED("Faltou tempo."),
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
    val hintRevealedAtEpochMillis: Long? = null,
    val endedAtEpochMillis: Long? = null,
    val errorCount: Int = 0,
    val failureReason: IpsumFailureReason? = null,
    val status: IpsumStatus,
)

data class IpsumResult(val ipsum: Ipsum, val errorVerdicts: List<String>)

fun Ipsum.totalTimeMillis(): Long? = endedAtEpochMillis?.let {
    elapsedIpsumMillis(startedAtEpochMillis, it)
}

fun Ipsum.problemUrl(): String =
    "https://codeforces.com/contest/${problem.id.contestId}/problem/${problem.id.index}"

fun elapsedIpsumMillis(startedAtEpochMillis: Long, nowEpochMillis: Long): Long =
    (nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0L)
