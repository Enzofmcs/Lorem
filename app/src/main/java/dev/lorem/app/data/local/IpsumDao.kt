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
}
