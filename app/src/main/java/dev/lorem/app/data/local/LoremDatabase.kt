package dev.lorem.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProblemHistoryEntity::class, ProblemCatalogEntity::class, IpsumEntity::class, IpsumSubmissionEntity::class],
    version = 7,
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()

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

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ipsums ADD COLUMN endedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE ipsums ADD COLUMN errorCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS ipsum_submissions (
                        submissionId INTEGER NOT NULL PRIMARY KEY,
                        ipsumId INTEGER NOT NULL,
                        verdict TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL
                    )""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ipsum_submissions_ipsumId ON ipsum_submissions(ipsumId)")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ipsums ADD COLUMN failureReason TEXT")
            }
        }
    }
}
