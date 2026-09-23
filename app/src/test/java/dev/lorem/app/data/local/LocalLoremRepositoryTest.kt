package dev.lorem.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lorem.app.domain.model.LocalProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalLoremRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `validated profile is atomically restored by a new repository instance`() = runTest {
        val storageFile = temporaryFolder.newFile("profile.preferences_pb")
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val expected = LocalProfile(
            handle = "tourist",
            displayName = "Gennady Korotkevich",
            officialRating = 4009,
            loremRating = 4009,
            consolidatedRating = null,
            lastSyncEpochMillis = 1234L,
        )

        val firstRepository = repository(storageFile, firstScope)
        firstRepository.saveProfile(expected)
        assertEquals(expected, firstRepository.profile.first())
        firstScope.cancel()

        val restoredRepository = repository(storageFile, backgroundScope)
        assertEquals(expected, restoredRepository.profile.first())
    }

    @Test
    fun `legacy display name is explicitly discarded`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("legacy.preferences_pb") },
        )
        dataStore.edit { it[stringPreferencesKey("display_name")] = "Ada" }
        val repository = LocalLoremRepository(dataStore, FakeProblemHistoryDao(), FakeProblemCatalogDao(), FakeIpsumDao())

        assertNull(repository.profile.first())
        repository.clearProfile()
        assertNull(dataStore.data.first()[stringPreferencesKey("display_name")])
    }

    private fun repository(file: File, scope: CoroutineScope) = LocalLoremRepository(
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
        problemHistoryDao = FakeProblemHistoryDao(),
        problemCatalogDao = FakeProblemCatalogDao(),
        ipsumDao = FakeIpsumDao(),
    )

    private class FakeProblemCatalogDao : ProblemCatalogDao {
        private val catalog = MutableStateFlow<List<ProblemCatalogEntity>>(emptyList())
        override fun observeAll() = catalog
        override suspend fun insertAll(problems: List<ProblemCatalogEntity>) { catalog.value += problems }
        override suspend fun deleteAll() { catalog.value = emptyList() }
    }

    private class FakeProblemHistoryDao : ProblemHistoryDao {
        private val history = MutableStateFlow<List<ProblemHistoryEntity>>(emptyList())

        override fun observeForOwner(ownerHandle: String) = MutableStateFlow(
            history.value.filter { it.ownerHandle == ownerHandle },
        )

        override suspend fun insertIfAbsent(history: ProblemHistoryEntity) {
            if (this.history.value.none {
                    it.ownerHandle == history.ownerHandle &&
                        it.contestId == history.contestId &&
                        it.problemIndex == history.problemIndex
                }
            ) {
                this.history.value += history
            }
        }

        override suspend fun promoteExisting(
            ownerHandle: String,
            contestId: Long,
            problemIndex: String,
            attempted: Boolean,
            hasAcceptedSubmission: Boolean,
        ) {
            history.value = history.value.map { entry ->
                if (entry.ownerHandle == ownerHandle && entry.contestId == contestId && entry.problemIndex == problemIndex) {
                    entry.copy(
                        attempted = entry.attempted || attempted,
                        hasAcceptedSubmission = entry.hasAcceptedSubmission || hasAcceptedSubmission,
                    )
                } else {
                    entry
                }
            }
        }
    }

    private class FakeIpsumDao : IpsumDao {
        override fun observeForOwner(ownerHandle: String) = MutableStateFlow<List<IpsumEntity>>(emptyList())
        override fun observeActive() = MutableStateFlow<IpsumEntity?>(null)
        override suspend fun insert(ipsum: IpsumEntity) = 1L
        override suspend fun revealHintOnce(ipsumId: Long, revealedAt: Long) = 0
    }
}
