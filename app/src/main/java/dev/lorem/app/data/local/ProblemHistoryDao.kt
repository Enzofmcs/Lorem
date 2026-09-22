package dev.lorem.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProblemHistoryDao {
    @Query("SELECT * FROM problem_history ORDER BY contestId, problemIndex")
    fun observeAll(): Flow<List<ProblemHistoryEntity>>

    @Upsert
    suspend fun upsertAll(history: List<ProblemHistoryEntity>)
}
