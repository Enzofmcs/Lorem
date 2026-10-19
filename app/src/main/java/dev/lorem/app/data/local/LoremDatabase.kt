package dev.lorem.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val LOREM_DATABASE_VERSION = 14

@Database(
    entities = [ProblemHistoryEntity::class, ProblemCatalogEntity::class, IpsumEntity::class, IpsumSubmissionEntity::class,
        AccountHandleEntity::class, AccountSelectionEntity::class, LegacyRolloutEntity::class],
    version = LOREM_DATABASE_VERSION,
    exportSchema = false,
)
abstract class LoremDatabase : RoomDatabase() {
    abstract fun problemHistoryDao(): ProblemHistoryDao
    abstract fun problemCatalogDao(): ProblemCatalogDao
    abstract fun ipsumDao(): IpsumDao
    abstract fun legacyRolloutDao(): LegacyRolloutDao
    abstract fun accountHandlesDao(): AccountHandlesDao

    companion object {
        fun create(context: Context): LoremDatabase = Room.databaseBuilder(
            context,
            LoremDatabase::class.java,
            "lorem.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14).addCallback(ARCHIVE_CALLBACK).build()

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS legacy_rollout (id INTEGER NOT NULL PRIMARY KEY, phase TEXT NOT NULL, profileJson TEXT, activeIpsumId INTEGER, cutoffAt INTEGER, failure TEXT)")
            }
        }
        internal val ARCHIVE_CALLBACK = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                for (table in listOf("ipsums", "ipsum_submissions", "problem_history")) {
                    for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
                        db.execSQL("CREATE TRIGGER IF NOT EXISTS freeze_${table}_${operation} BEFORE $operation ON $table WHEN EXISTS (SELECT 1 FROM legacy_rollout WHERE phase = 'FROZEN') BEGIN SELECT RAISE(ABORT, 'Legacy archive is read only'); END")
                    }
                }
                db.execSQL("CREATE TRIGGER IF NOT EXISTS freeze_rollout BEFORE UPDATE ON legacy_rollout WHEN OLD.phase = 'FROZEN' BEGIN SELECT RAISE(ABORT, 'Legacy archive is read only'); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS retain_rollout BEFORE DELETE ON legacy_rollout BEGIN SELECT RAISE(ABORT, 'Legacy archive must be preserved'); END")
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS account_handles (
                    uid TEXT NOT NULL, handleKey TEXT NOT NULL, bindingVersion INTEGER NOT NULL,
                    handle TEXT NOT NULL, loremRating INTEGER NOT NULL, consolidatedRating INTEGER,
                    progressVersion INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL,
                    PRIMARY KEY(uid, handleKey, bindingVersion))""".trimIndent())
                db.execSQL("""CREATE TABLE IF NOT EXISTS account_selections (
                    uid TEXT NOT NULL PRIMARY KEY, handleKey TEXT, bindingVersion INTEGER,
                    selectionVersion INTEGER NOT NULL, locked INTEGER NOT NULL)""".trimIndent())
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ipsums ADD COLUMN initialCodeforcesRating INTEGER")
                db.execSQL("ALTER TABLE ipsums ADD COLUMN initialCodeforcesRatingObservedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE ipsums ADD COLUMN studyRecordingVersion TEXT")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE problem_catalog ADD COLUMN originMetadata TEXT")
                db.execSQL("ALTER TABLE ipsums ADD COLUMN problemOriginMetadata TEXT")
            }
        }

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

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ipsums ADD COLUMN ratingDelta INTEGER")
                db.execSQL("ALTER TABLE ipsums ADD COLUMN finalLoremRating INTEGER")
                db.execSQL("ALTER TABLE ipsums ADD COLUMN expectedTimeMillis INTEGER")
            }
        }


        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ipsums ADD COLUMN solvedOutsideAtEpochMillis INTEGER")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ipsums ADD COLUMN recommendationFactors TEXT")
            }
        }
    }
}
