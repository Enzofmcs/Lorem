package dev.lorem.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProblemHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LoremDatabase : RoomDatabase() {
    abstract fun problemHistoryDao(): ProblemHistoryDao

    companion object {
        fun create(context: Context): LoremDatabase = Room.databaseBuilder(
            context,
            LoremDatabase::class.java,
            "lorem.db",
        ).build()
    }
}
