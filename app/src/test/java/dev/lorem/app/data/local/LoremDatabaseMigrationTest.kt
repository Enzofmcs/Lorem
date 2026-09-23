package dev.lorem.app.data.local

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoremDatabaseMigrationTest {
    @Test
    fun `migration 4 to 5 preserves rows and adds nullable hint time`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration-${System.nanoTime()}.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        val sqlite = helper.writableDatabase
        sqlite.execSQL("""CREATE TABLE ipsums (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerHandle TEXT NOT NULL,
            contestId INTEGER NOT NULL, problemIndex TEXT NOT NULL, problemName TEXT NOT NULL,
            problemTags TEXT NOT NULL, initialLoremRating INTEGER NOT NULL, category TEXT NOT NULL,
            desiredRatingMin INTEGER NOT NULL, desiredRatingMax INTEGER NOT NULL,
            selectedRating INTEGER NOT NULL, fallbackDistance INTEGER NOT NULL,
            startedAtEpochMillis INTEGER NOT NULL, status TEXT NOT NULL, activeSlot INTEGER
        )""".trimIndent())
        sqlite.execSQL(
            "INSERT INTO ipsums VALUES (1, 'tourist', 100, 'A', 'Problem', 'dp', 800, 'CURRENT_LEVEL', 800, 800, 800, 0, 1234, 'ACTIVE', 1)",
        )

        LoremDatabase.MIGRATION_4_5.migrate(sqlite)

        sqlite.query("SELECT startedAtEpochMillis, hintRevealedAtEpochMillis FROM ipsums").use {
            it.moveToFirst()
            assertEquals(1234L, it.getLong(0))
            assertEquals(true, it.isNull(1))
        }
        helper.close()
    }
}
