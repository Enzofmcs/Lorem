package dev.lorem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
}
