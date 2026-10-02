package dev.lorem.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import dev.lorem.app.domain.model.IpsumSubmission
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.LocalProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class IpsumPersistenceTest {
    @Test
    fun `active ipsum and complete recommendation audit survive repository recreation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LoremDatabase::class.java).build()
        val file = File(context.cacheDir, "ipsum-${System.nanoTime()}.preferences_pb")
        val repository = repository(database, file, backgroundScope)

        val saved = repository.createActiveIpsum(ipsum())
        val recreated = repository(database, File(context.cacheDir, "recreated-${System.nanoTime()}.preferences_pb"), backgroundScope)
        val restored = recreated.activeIpsum.first()!!

        assertEquals(saved.id, restored.id)
        assertEquals(1234L, restored.startedAtEpochMillis)
        assertEquals(IpsumCategory.CHALLENGE, restored.category)
        assertEquals(1300, restored.desiredRatingMin)
        assertEquals(1400, restored.desiredRatingMax)
        assertEquals(1500, restored.selectedRating)
        assertEquals(100, restored.fallbackDistance)
    }

    @Test
    fun `database unique index rejects a second active ipsum`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LoremDatabase::class.java).build()
        val repository = repository(database, File(context.cacheDir, "unique-${System.nanoTime()}.preferences_pb"), backgroundScope)
        repository.createActiveIpsum(ipsum())

        try {
            repository.createActiveIpsum(ipsum().copy(problem = problem(2)))
            fail("second active Ipsum should be rejected")
        } catch (_: ActiveIpsumAlreadyExistsException) {
            // Expected: enforced by the unique active slot in SQLite.
        }
        assertEquals(1, database.ipsumDao().observeForOwner("tourist").first().size)
    }

    @Test
    fun `first hint reveal is persisted and later reveals do not replace its time`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LoremDatabase::class.java).build()
        val repository = repository(database, File(context.cacheDir, "hint-${System.nanoTime()}.preferences_pb"), backgroundScope)
        val saved = repository.createActiveIpsum(ipsum())

        repository.revealIpsumHint(saved.id, 2_000L)
        repository.revealIpsumHint(saved.id, 3_000L)

        assertEquals(2_000L, repository.activeIpsum.first()?.hintRevealedAtEpochMillis)
    }

    @Test
    fun `submissions completion and error count survive reopening and are idempotent`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseFile = File(context.cacheDir, "ipsum-db-${System.nanoTime()}")
        val preferences = File(context.cacheDir, "ipsum-state-${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { preferences })
        var database = Room.databaseBuilder(context, LoremDatabase::class.java, databaseFile.absolutePath).build()
        var repository = repository(database, dataStore)
        repository.saveProfile(profile())
        val saved = repository.createActiveIpsum(ipsum())
        val values = listOf(
            IpsumSubmission(10, saved.id, "WRONG_ANSWER", 2_000),
            IpsumSubmission(11, saved.id, "COMPILATION_ERROR", 3_000),
            IpsumSubmission(12, saved.id, "OK", 4_000),
            IpsumSubmission(13, saved.id, "RUNTIME_ERROR", 5_000),
        )
        val attempts = listOf(
            async { repository.recordIpsumSubmissions(saved.id, values) },
            async { repository.recordIpsumSubmissions(saved.id, values) },
        ).map { it.await() }
        database.close()
        database = Room.databaseBuilder(context, LoremDatabase::class.java, databaseFile.absolutePath).build()
        repository = repository(database, dataStore)
        val restored = database.ipsumDao().find(saved.id)!!.toDomain()

        assertEquals(4, attempts.sumOf { it.insertedCount })
        assertEquals(1, attempts.count { it.completed })
        assertEquals(listOf(2, 2), attempts.map { it.errorCount })
        assertEquals(IpsumStatus.COMPLETED, restored.status)
        assertEquals(2, restored.errorCount)
        assertEquals(4_000L, restored.endedAtEpochMillis)
        assertEquals(35, restored.ratingDelta)
        assertEquals(1235, restored.finalLoremRating)
        assertEquals(90 * 60_000L, restored.expectedTimeMillis)
        assertEquals(1235, repository.profile.first()?.loremRating)
        assertEquals(
            listOf("WRONG_ANSWER", "COMPILATION_ERROR"),
            repository.getIpsumResult(saved.id)?.errorVerdicts,
        )
        assertEquals(null, repository.activeIpsum.first())
        database.close()
    }

    @Test
    fun `manual result is atomic persistent and cannot be ended twice`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseFile = File(context.cacheDir, "manual-db-${System.nanoTime()}")
        val preferences = File(context.cacheDir, "manual-state-${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { preferences })
        var database = Room.databaseBuilder(context, LoremDatabase::class.java, databaseFile.absolutePath).build()
        var repository = repository(database, dataStore)
        repository.saveProfile(profile())
        val saved = repository.createActiveIpsum(ipsum().copy(hintRevealedAtEpochMillis = 1_500L))
        repository.recordIpsumSubmissions(saved.id, listOf(
            IpsumSubmission(20, saved.id, "WRONG_ANSWER", 2_000),
            IpsumSubmission(21, saved.id, "TIME_LIMIT_EXCEEDED", 3_000),
        ))

        assertEquals(true, repository.endIpsumWithoutAc(saved.id, IpsumFailureReason.LOGIC_NOT_FOUND, 5_000L))
        assertEquals(false, repository.endIpsumWithoutAc(saved.id, IpsumFailureReason.TIME_EXPIRED, 9_000L))
        database.close()

        database = Room.databaseBuilder(context, LoremDatabase::class.java, databaseFile.absolutePath).build()
        repository = repository(database, dataStore)
        val result = repository.getIpsumResult(saved.id)!!

        assertEquals(IpsumStatus.PENDING, result.ipsum.status)
        assertEquals(IpsumFailureReason.LOGIC_NOT_FOUND, result.ipsum.failureReason)
        assertEquals(5_000L, result.ipsum.endedAtEpochMillis)
        assertEquals(1_500L, result.ipsum.hintRevealedAtEpochMillis)
        assertEquals(2, result.ipsum.errorCount)
        assertEquals(-12, result.ipsum.ratingDelta)
        assertEquals(1188, result.ipsum.finalLoremRating)
        assertEquals(90 * 60_000L, result.ipsum.expectedTimeMillis)
        assertEquals(1188, repository.profile.first()?.loremRating)
        repository.getIpsumResult(saved.id)
        repository.recordIpsumSubmissions(
            saved.id,
            listOf(IpsumSubmission(22, saved.id, "OK", 10_000L)),
        )
        assertEquals(1188, repository.profile.first()?.loremRating)
        assertEquals(-12, repository.getIpsumResult(saved.id)?.ipsum?.ratingDelta)
        assertEquals(listOf("WRONG_ANSWER", "TIME_LIMIT_EXCEEDED"), result.errorVerdicts)
        assertEquals(null, repository.activeIpsum.first())
        database.close()
    }

    private fun repository(database: LoremDatabase, file: File, scope: CoroutineScope) = LocalLoremRepository(
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
        problemHistoryDao = database.problemHistoryDao(),
        problemCatalogDao = database.problemCatalogDao(),
        ipsumDao = database.ipsumDao(),
    )

    private fun repository(database: LoremDatabase, dataStore: DataStore<Preferences>) = LocalLoremRepository(
        dataStore = dataStore,
        problemHistoryDao = database.problemHistoryDao(),
        problemCatalogDao = database.problemCatalogDao(),
        ipsumDao = database.ipsumDao(),
    )

    private fun ipsum() = Ipsum(
        id = 0,
        ownerHandle = "tourist",
        problem = problem(1),
        initialLoremRating = 1200,
        category = IpsumCategory.CHALLENGE,
        desiredRatingMin = 1300,
        desiredRatingMax = 1400,
        selectedRating = 1500,
        fallbackDistance = 100,
        startedAtEpochMillis = 1234,
        status = IpsumStatus.ACTIVE,
    )

    private fun problem(id: Long) = CodeforcesProblem(ProblemId(id, "A"), "Problem $id", 1500, setOf("dp"))

    private fun profile() = LocalProfile("tourist", "Tourist", 1200, 1200, null, 1)
}
