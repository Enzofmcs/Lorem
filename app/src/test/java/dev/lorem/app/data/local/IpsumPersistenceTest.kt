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
import kotlinx.coroutines.flow.first
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
