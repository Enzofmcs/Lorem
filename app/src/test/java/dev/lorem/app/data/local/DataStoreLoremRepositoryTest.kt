package dev.lorem.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.lorem.app.domain.model.LocalProfile
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

class DataStoreLoremRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `saved profile is restored by a new repository instance`() = runTest {
        val storageFile = temporaryFolder.newFile("profile.preferences_pb")
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))

        val firstRepository = repository(storageFile, firstScope)
        firstRepository.saveProfile(LocalProfile("Ada"))
        assertEquals(LocalProfile("Ada"), firstRepository.profile.first())
        firstScope.cancel()

        val restoredRepository = repository(storageFile, backgroundScope)
        assertEquals(LocalProfile("Ada"), restoredRepository.profile.first())
    }

    private fun repository(file: File, scope: CoroutineScope) = DataStoreLoremRepository(
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        ),
    )
}
