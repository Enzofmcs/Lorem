package dev.lorem.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumCategory
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import dev.lorem.app.domain.model.IpsumSubmission
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
        var database = Room.databaseBuilder(context, LoremDatabase::class.java, databaseFile.absolutePath).build()
        var repository = repository(database, preferences, backgroundScope)
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
        repository = repository(database, preferences, backgroundScope)
        val restored = database.ipsumDao().find(saved.id)!!.toDomain()

        assertEquals(4, attempts.sumOf { it.insertedCount })
        assertEquals(1, attempts.count { it.completed })
        assertEquals(listOf(2, 2), attempts.map { it.errorCount })
        assertEquals(IpsumStatus.COMPLETED, restored.status)
        assertEquals(2, restored.errorCount)
        assertEquals(4_000L, restored.endedAtEpochMillis)
        assertEquals(null, repository.activeIpsum.first())
        database.close()
    }

    private fun repository(database: LoremDatabase, file: File, scope: CoroutineScope) = LocalLoremRepository(
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
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
}
