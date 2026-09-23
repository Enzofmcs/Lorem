package dev.lorem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.repository.IpsumUpdate
import kotlinx.coroutines.flow.Flow

@Dao
interface IpsumDao {
    @Query("SELECT * FROM ipsums WHERE ownerHandle = :ownerHandle ORDER BY id")
    fun observeForOwner(ownerHandle: String): Flow<List<IpsumEntity>>

    @Query("SELECT * FROM ipsums WHERE activeSlot = 1 LIMIT 1")
    fun observeActive(): Flow<IpsumEntity?>

    @Insert
    suspend fun insert(ipsum: IpsumEntity): Long

    @Query("UPDATE ipsums SET hintRevealedAtEpochMillis = :revealedAt WHERE id = :ipsumId AND hintRevealedAtEpochMillis IS NULL")
    suspend fun revealHintOnce(ipsumId: Long, revealedAt: Long): Int

    @Query("SELECT * FROM ipsums WHERE id = :ipsumId")
    suspend fun find(ipsumId: Long): IpsumEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubmissions(submissions: List<IpsumSubmissionEntity>): List<Long>

    @Query("SELECT * FROM ipsum_submissions WHERE ipsumId = :ipsumId ORDER BY createdAtEpochMillis, submissionId")
    suspend fun submissions(ipsumId: Long): List<IpsumSubmissionEntity>

    @Query("UPDATE ipsums SET status = 'COMPLETED', activeSlot = NULL, endedAtEpochMillis = :endedAt, errorCount = :errorCount WHERE id = :ipsumId AND status = 'ACTIVE'")
    suspend fun completeOnce(ipsumId: Long, endedAt: Long, errorCount: Int): Int

    @Query("UPDATE ipsums SET status = 'PENDING', activeSlot = NULL, endedAtEpochMillis = :endedAt, failureReason = :reason, errorCount = :errorCount WHERE id = :ipsumId AND status = 'ACTIVE'")
    suspend fun endWithoutAcOnce(ipsumId: Long, reason: String, endedAt: Long, errorCount: Int): Int

    @Transaction
    suspend fun endWithoutAc(ipsumId: Long, reason: String, endedAt: Long): Boolean {
        val current = find(ipsumId) ?: return false
        if (current.status != IpsumStatus.ACTIVE.name) return false
        val errors = submissions(ipsumId).count { it.verdict != "OK" }
        return endWithoutAcOnce(ipsumId, reason, endedAt, errors) == 1
    }

    @Transaction
    suspend fun record(ipsumId: Long, values: List<IpsumSubmissionEntity>): IpsumUpdate {
        val current = find(ipsumId) ?: return IpsumUpdate(0, 0, false)
        if (current.status != IpsumStatus.ACTIVE.name) {
            return IpsumUpdate(0, current.errorCount, false)
        }
        val inserted = insertSubmissions(values).count { it != -1L }
        val ordered = submissions(ipsumId)
        val firstAccepted = ordered.firstOrNull { it.verdict == "OK" }
        val errors = ordered.count {
            it.verdict != "OK" && (firstAccepted == null ||
                it.createdAtEpochMillis < firstAccepted.createdAtEpochMillis ||
                it.createdAtEpochMillis == firstAccepted.createdAtEpochMillis && it.submissionId < firstAccepted.submissionId)
        }
        val completed = firstAccepted != null && completeOnce(
            ipsumId,
            firstAccepted.createdAtEpochMillis,
            errors,
        ) == 1
        return IpsumUpdate(inserted, errors, completed)
    }
}
