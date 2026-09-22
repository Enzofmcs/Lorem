package dev.lorem.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.annotation.Config

@Config(sdk = [35])
class ProblemHistoryPersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
        firstRepository.saveProblemHistory(expected)
        assertEquals(expected, firstRepository.problemHistory.first())
        firstDatabase.close()
        firstScope.cancel()

        val reopenedDatabase = database(databaseFile)
        val reopenedRepository = repository(preferencesFile, backgroundScope, reopenedDatabase)
        assertEquals(expected, reopenedRepository.problemHistory.first())
        reopenedDatabase.close()
    }

    @Test
    fun `saving the same import again does not duplicate problems`() = runTest {
        val database = database(temporaryFolder.newFile("idempotent.db"))
        val repository = repository(
            temporaryFolder.newFile("idempotent.preferences_pb"),
            backgroundScope,
            database,
        )
        val history = listOf(history(contestId = 200L, index = "C", accepted = true))

        repository.saveProblemHistory(history)
        repository.saveProblemHistory(history)

        assertEquals(history, repository.problemHistory.first())
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
    )

    private fun history(contestId: Long, index: String, accepted: Boolean) = ProblemHistory(
        problemId = ProblemId(contestId = contestId, index = index),
        attempted = true,
        hasAcceptedSubmission = accepted,
    )
}
