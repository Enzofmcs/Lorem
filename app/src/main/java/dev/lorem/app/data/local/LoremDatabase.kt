package dev.lorem.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProblemHistoryEntity::class, ProblemCatalogEntity::class, IpsumEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class LoremDatabase : RoomDatabase() {
    abstract fun problemHistoryDao(): ProblemHistoryDao
    abstract fun problemCatalogDao(): ProblemCatalogDao
    abstract fun ipsumDao(): IpsumDao

    companion object {
        fun create(context: Context): LoremDatabase = Room.databaseBuilder(
            context,
            LoremDatabase::class.java,
            "lorem.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS problem_catalog (
                        contestId INTEGER NOT NULL,
                        problemIndex TEXT NOT NULL,
                        name TEXT NOT NULL,
                        rating INTEGER NOT NULL,
                        tags TEXT NOT NULL,
                        PRIMARY KEY(contestId, problemIndex)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ipsums (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ownerHandle TEXT NOT NULL,
                        contestId INTEGER NOT NULL,
                        problemIndex TEXT NOT NULL,
                        problemName TEXT NOT NULL,
                        problemTags TEXT NOT NULL,
                        initialLoremRating INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        desiredRatingMin INTEGER NOT NULL,
                        desiredRatingMax INTEGER NOT NULL,
                        selectedRating INTEGER NOT NULL,
                        fallbackDistance INTEGER NOT NULL,
                        startedAtEpochMillis INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        activeSlot INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ipsums_activeSlot ON ipsums(activeSlot)")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ipsums ADD COLUMN hintRevealedAtEpochMillis INTEGER")
            }
        }
    }
}
