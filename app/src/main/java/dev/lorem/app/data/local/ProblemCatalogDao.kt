package dev.lorem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProblemCatalogDao {
    @Query("SELECT * FROM problem_catalog ORDER BY contestId, problemIndex")
    fun observeAll(): Flow<List<ProblemCatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(problems: List<ProblemCatalogEntity>)

    @Query("DELETE FROM problem_catalog")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(problems: List<ProblemCatalogEntity>) {
        deleteAll()
        insertAll(problems)
    }
}
