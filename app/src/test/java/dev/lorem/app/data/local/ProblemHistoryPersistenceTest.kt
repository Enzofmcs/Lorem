package dev.lorem.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.ProblemHistorySyncResult
import dev.lorem.app.domain.SynchronizeProblemHistory
import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.CodeforcesSubmission
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.UserLookupResult
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProblemHistoryPersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `catalog and timestamp survive reopening while refresh preserves problem history`() = runTest {
        val databaseFile = temporaryFolder.newFile("catalog.db")
        val preferencesFile = temporaryFolder.newFile("catalog.preferences_pb")
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val firstDatabase = database(databaseFile)
        val firstRepository = repository(preferencesFile, firstScope, firstDatabase)
        val history = history(99L, "A", accepted = true)
        firstRepository.saveProfile(profile())
        firstRepository.saveProblemHistory("tourist", listOf(history))
        firstRepository.replaceProblemCatalog(
            listOf(
                problem(1L, "A", "First", 800, setOf("math")),
                problem(2L, "B", "Unrated", null, setOf("graphs")),
            ),
            1_000L,
        )
        firstRepository.replaceProblemCatalog(
            listOf(problem(1L, "A", "Updated", 900, setOf("implementation"))),
            2_000L,
        )
        assertEquals(listOf(history), firstRepository.problemHistory.first())
        firstDatabase.close()
        firstScope.cancel()

        val reopenedDatabase = database(databaseFile)
        val reopened = repository(preferencesFile, backgroundScope, reopenedDatabase)
        assertEquals(
            listOf(problem(1L, "A", "Updated", 900, setOf("implementation"))),
            reopened.problemCatalog.first(),
        )
        assertEquals(2_000L, reopened.catalogLastUpdatedEpochMillis.first())
        assertEquals(listOf(history), reopened.problemHistory.first())
        reopenedDatabase.close()
    }

    @Test
    fun `accepted and attempted entries survive reopening structured storage`() = runTest {
        val databaseFile = temporaryFolder.newFile("history.db")
        val preferencesFile = temporaryFolder.newFile("profile.preferences_pb")
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val expected = listOf(
            history(contestId = 100L, index = "A", accepted = false),
            history(contestId = 100L, index = "B", accepted = true),
            history(contestId = 101L, index = "A", accepted = true),
        )

        val firstDatabase = database(databaseFile)
        val firstRepository = repository(preferencesFile, firstScope, firstDatabase)
        firstRepository.saveProfile(profile())
        firstRepository.saveProblemHistory("tourist", expected)
        assertEquals(expected, firstRepository.problemHistory.first())
        firstDatabase.close()
        firstScope.cancel()

        val reopenedDatabase = database(databaseFile)
        val reopenedRepository = repository(preferencesFile, backgroundScope, reopenedDatabase)
        assertEquals(expected, reopenedRepository.problemHistory.first())
        reopenedDatabase.close()
    }

    @Test
    fun `synchronizing the same submissions twice preserves identical rows`() = runTest {
        val database = database(temporaryFolder.newFile("idempotent.db"))
        val repository = repository(
            temporaryFolder.newFile("idempotent.preferences_pb"),
            backgroundScope,
            database,
        )
        repository.saveProfile(profile())
        val submissions = listOf(
            submission(id = 1L, contestId = 200L, index = "C", verdict = "OK"),
            submission(id = 2L, contestId = 201L, index = "A", verdict = "WRONG_ANSWER"),
        )
        val synchronize = SynchronizeProblemHistory(repository, FakeCodeforcesRepository(submissions))

        assertEquals(ProblemHistorySyncResult.Success(2), synchronize())
        val firstImport = repository.problemHistory.first()
        assertEquals(ProblemHistorySyncResult.Success(2), synchronize())

        assertEquals(2, repository.problemHistory.first().size)
        assertEquals(firstImport, repository.problemHistory.first())
        database.close()
    }

    @Test
    fun `later accepted submission promotes one row and older wrong answer cannot regress it`() = runTest {
        val database = database(temporaryFolder.newFile("promotion.db"))
        val repository = repository(
            temporaryFolder.newFile("promotion.preferences_pb"),
            backgroundScope,
            database,
        )
        repository.saveProfile(profile())
        val codeforces = FakeCodeforcesRepository(
            listOf(submission(id = 1L, contestId = 300L, index = "B", verdict = "WRONG_ANSWER")),
        )
        val synchronize = SynchronizeProblemHistory(repository, codeforces)

        synchronize()
        assertEquals(listOf(history(300L, "B", accepted = false)), repository.problemHistory.first())

        codeforces.submissions = listOf(submission(id = 2L, contestId = 300L, index = "B", verdict = "OK"))
        synchronize()
        assertEquals(listOf(history(300L, "B", accepted = true)), repository.problemHistory.first())

        codeforces.submissions = listOf(
            submission(id = 1L, contestId = 300L, index = "B", verdict = "WRONG_ANSWER"),
        )
        synchronize()
        assertEquals(listOf(history(300L, "B", accepted = true)), repository.problemHistory.first())
        database.close()
    }

    @Test
    fun `histories owned by different normalized handles are never combined`() = runTest {
        val database = database(temporaryFolder.newFile("owners.db"))
        val repository = repository(
            temporaryFolder.newFile("owners.preferences_pb"),
            backgroundScope,
            database,
        )
        val touristHistory = history(400L, "A", accepted = true)
        val otherHistory = history(500L, "B", accepted = false)

        repository.saveProfile(profile(handle = "Tourist"))
        repository.saveProblemHistory("TOURIST", listOf(touristHistory))
        repository.saveProblemHistory("other", listOf(otherHistory))
        assertEquals(listOf(touristHistory), repository.problemHistory.first())

        repository.saveProfile(profile(handle = "Other"))
        assertEquals(listOf(otherHistory), repository.problemHistory.first())

        repository.saveProfile(profile(handle = "tourist"))
        assertEquals(listOf(touristHistory), repository.problemHistory.first())
        database.close()
    }

    private fun database(file: File): LoremDatabase = Room.databaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LoremDatabase::class.java,
        file.absolutePath,
    ).build()

    private fun repository(
        preferencesFile: File,
        scope: CoroutineScope,
        database: LoremDatabase,
    ) = LocalLoremRepository(
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { preferencesFile },
        ),
        problemHistoryDao = database.problemHistoryDao(),
        problemCatalogDao = database.problemCatalogDao(),
        ipsumDao = database.ipsumDao(),
    )

    private fun history(contestId: Long, index: String, accepted: Boolean) = ProblemHistory(
        problemId = ProblemId(contestId = contestId, index = index),
        attempted = true,
        hasAcceptedSubmission = accepted,
    )

    private fun problem(
        contestId: Long,
        index: String,
        name: String,
        rating: Int?,
        tags: Set<String>,
    ) = CodeforcesProblem(ProblemId(contestId, index), name, rating, tags)

    private fun profile(handle: String = "tourist") = LocalProfile(
        handle = handle,
        displayName = "Tourist",
        officialRating = null,
        loremRating = 1500,
        consolidatedRating = null,
        lastSyncEpochMillis = 0L,
    )

    private fun submission(id: Long, contestId: Long, index: String, verdict: String?) = CodeforcesSubmission(
        id = id,
        problemId = ProblemId(contestId, index),
        verdict = verdict,
        createdAt = Instant.ofEpochSecond(id),
    )

    private class FakeCodeforcesRepository(
        var submissions: List<CodeforcesSubmission>,
    ) : CodeforcesRepository {
        override suspend fun user(handle: String): UserLookupResult = error("Not used")

        override suspend fun submissionHistory(handle: String) = SubmissionHistoryResult.Success(submissions)

        override suspend fun problems() = error("Not used")
    }
}
