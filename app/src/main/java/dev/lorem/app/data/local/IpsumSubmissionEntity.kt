package dev.lorem.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.lorem.app.domain.model.IpsumSubmission

@Entity(tableName = "ipsum_submissions", indices = [Index("ipsumId")])
data class IpsumSubmissionEntity(
    @PrimaryKey val submissionId: Long,
    val ipsumId: Long,
    val verdict: String,
    val createdAtEpochMillis: Long,
)

fun IpsumSubmission.toEntity() = IpsumSubmissionEntity(submissionId, ipsumId, verdict, createdAtEpochMillis)
