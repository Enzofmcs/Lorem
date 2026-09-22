package dev.lorem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProblemHistoryDao {
    @Query("SELECT * FROM problem_history ORDER BY contestId, problemIndex")
    fun observeAll(): Flow<List<ProblemHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(history: ProblemHistoryEntity)

    @Query(
        """
        UPDATE problem_history
        SET attempted = attempted OR :attempted,
            hasAcceptedSubmission = hasAcceptedSubmission OR :hasAcceptedSubmission
        WHERE contestId = :contestId AND problemIndex = :problemIndex
        """,
    )
    suspend fun promoteExisting(
        contestId: Long,
        problemIndex: String,
        attempted: Boolean,
        hasAcceptedSubmission: Boolean,
    )

    @Transaction
    suspend fun upsertAll(history: List<ProblemHistoryEntity>) {
        history.forEach { entry ->
            insertIfAbsent(entry)
            promoteExisting(
                contestId = entry.contestId,
                problemIndex = entry.problemIndex,
                attempted = entry.attempted,
                hasAcceptedSubmission = entry.hasAcceptedSubmission,
            )
        }
    }
}
