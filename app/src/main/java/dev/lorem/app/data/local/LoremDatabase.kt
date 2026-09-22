package dev.lorem.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProblemHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class LoremDatabase : RoomDatabase() {
    abstract fun problemHistoryDao(): ProblemHistoryDao

    companion object {
        fun create(context: Context): LoremDatabase = Room.databaseBuilder(
            context,
            LoremDatabase::class.java,
            "lorem.db",
        ).addMigrations(MIGRATION_1_2).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE problem_history_new (
                        ownerHandle TEXT NOT NULL,
                        contestId INTEGER NOT NULL,
                        problemIndex TEXT NOT NULL,
                        attempted INTEGER NOT NULL,
                        hasAcceptedSubmission INTEGER NOT NULL,
                        PRIMARY KEY(ownerHandle, contestId, problemIndex)
                    )
                    """.trimIndent(),
                )
                // Version 1 did not record an owner, so those rows cannot safely be attributed.
                db.execSQL("DROP TABLE problem_history")
                db.execSQL("ALTER TABLE problem_history_new RENAME TO problem_history")
            }
        }
    }
}
