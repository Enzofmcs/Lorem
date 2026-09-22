package dev.lorem.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lorem.app.domain.model.LocalProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreLoremRepositoryTest {
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
        val repository = DataStoreLoremRepository(dataStore)

        assertNull(repository.profile.first())
        repository.clearProfile()
        assertNull(dataStore.data.first()[stringPreferencesKey("display_name")])
    }

    private fun repository(file: File, scope: CoroutineScope) = DataStoreLoremRepository(
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
    )
}
