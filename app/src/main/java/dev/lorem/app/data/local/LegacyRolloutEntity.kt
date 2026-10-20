package dev.lorem.app.data.local

import androidx.room.*

@Entity(tableName = "legacy_rollout")
data class LegacyRolloutEntity(
    @PrimaryKey val id: Int = 1,
    val phase: String,
    val profileJson: String?,
    val activeIpsumId: Long?,
    val cutoffAt: Long?,
    val failure: String?,
)

@Dao
interface LegacyRolloutDao {
    @Query("SELECT * FROM legacy_rollout WHERE id = 1")
    suspend fun state(): LegacyRolloutEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(state: LegacyRolloutEntity)
    @Update suspend fun update(state: LegacyRolloutEntity)
    @Query("UPDATE legacy_rollout SET cutoffAt = :cutoff, failure = :failure WHERE id = 1 AND phase = 'VERIFYING' AND cutoffAt IS NULL")
    suspend fun cutoffOnce(cutoff: Long, failure: String): Int
    @Query("SELECT * FROM ipsums ORDER BY id")
    suspend fun attempts(): List<IpsumEntity>
}
