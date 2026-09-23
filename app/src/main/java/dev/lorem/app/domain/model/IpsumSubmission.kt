package dev.lorem.app.domain.model

data class IpsumSubmission(
    val submissionId: Long,
    val ipsumId: Long,
    val verdict: String,
    val createdAtEpochMillis: Long,
)
